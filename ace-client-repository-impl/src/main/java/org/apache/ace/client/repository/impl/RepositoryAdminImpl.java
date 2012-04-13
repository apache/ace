/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.client.repository.impl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.impl.RepositoryAdminLoginContextImpl.RepositorySetDescriptor;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.ext.impl.CachedRepositoryImpl;
import org.apache.ace.repository.ext.impl.FilebasedBackupRepository;
import org.apache.ace.repository.ext.impl.RemoteRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.osgi.service.useradmin.User;

/**
 * An implementation of RepositoryAdmin, responsible for managing <code>ObjectRepositoryImpl</code>
 * descendants.<br>
 * The actual repository managing is delegated to <code>RepositorySet</code>s, while the logic
 * for binding these sets together is located in this class. Set actual <code>RepositorySet</code>s
 * to be used are defined in <code>login(...)</code>.<br>
 */
public class RepositoryAdminImpl implements RepositoryAdmin {
    /**
     * Maps from interface classes of the ObjectRepositories to their implementations.
     */
    @SuppressWarnings("unchecked")
    private Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> m_repositories;

    private final ChangeNotifier m_changeNotifier;
    private volatile BundleContext m_context; /* Injected by dependency manager */
    private volatile PreferencesService m_preferences; /* Injected by dependency manager */
    private volatile LogService m_log; /* Injected by dependency manager */

    private final Object m_lock = new Object();

    private final static String PREFS_LOCAL_FILE_ROOT = "ClientRepositoryAdmin";
    private final static String PREFS_LOCAL_FILE_LOCATION = "FileLocation";
    private final static String PREFS_LOCAL_FILE_CURRENT = "current";
    private final static String PREFS_LOCAL_FILE_BACKUP = "backup";
    private User m_user;
    private RepositorySet[] m_repositorySets;

    private volatile DependencyManager m_manager;
    List<Component[]> m_services;
    private ArtifactRepositoryImpl m_artifactRepositoryImpl;
    private FeatureRepositoryImpl m_featureRepositoryImpl;
    private Artifact2FeatureAssociationRepositoryImpl m_artifact2FeatureAssociationRepositoryImpl;
    private DistributionRepositoryImpl m_distributionRepositoryImpl;
    private Feature2DistributionAssociationRepositoryImpl m_feature2DistributionAssociationRepositoryImpl;
    private TargetRepositoryImpl m_targetRepositoryImpl;
    private Distribution2TargetAssociationRepositoryImpl m_distribution2TargetAssociationRepositoryImpl;
    private DeploymentVersionRepositoryImpl m_deploymentVersionRepositoryImpl;
    private ChangeNotifierManager m_changeNotifierManager;
    private final String m_sessionID;
    private final Properties m_sessionProps;

    public RepositoryAdminImpl(String sessionID) {
        m_sessionID = sessionID;
        m_sessionProps = new Properties();
        m_sessionProps.put(SessionFactory.SERVICE_SID, sessionID);
        m_changeNotifierManager = new ChangeNotifierManager();
        m_changeNotifier = m_changeNotifierManager.getConfiguredNotifier(RepositoryAdmin.PRIVATE_TOPIC_ROOT, RepositoryAdmin.PUBLIC_TOPIC_ROOT, RepositoryAdmin.TOPIC_ENTITY_ROOT, m_sessionID);
    }

    public Properties getSessionProps() {
        return m_sessionProps;
    }

    /**
     * Returns a list of instances that make up this composition. Instances are used to
     * inject dependencies into.
     *
     * @return list of instances
     */
    public Object[] getInstances() {
        return new Object[] { this, m_changeNotifierManager };
    }

    @SuppressWarnings("unchecked")
    void initialize(Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> repositories) {
        m_repositories = repositories;
    }

    @SuppressWarnings("unchecked")
    public void start() {
        synchronized (m_lock) {
            initialize(publishRepositories());
        }
    }

    public void stop() {
        pullRepositories();
        synchronized (m_lock) {
            if (loggedIn()) {
                try {
                    logout(true);
                }
                catch (IOException ioe) {
                    m_log.log(LogService.LOG_ERROR, "Failed to log out of the repositories.", ioe);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> publishRepositories() {
        // create the repository objects, if this is the first time this method is called.
        if (m_artifactRepositoryImpl == null) {
            m_artifactRepositoryImpl = new ArtifactRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, ArtifactObject.TOPIC_ENTITY_ROOT, m_sessionID));
            m_featureRepositoryImpl = new FeatureRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, FeatureObject.TOPIC_ENTITY_ROOT, m_sessionID));
            m_artifact2FeatureAssociationRepositoryImpl = new Artifact2FeatureAssociationRepositoryImpl(m_artifactRepositoryImpl, m_featureRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, Artifact2FeatureAssociation.TOPIC_ENTITY_ROOT, m_sessionID));
            m_distributionRepositoryImpl = new DistributionRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, DistributionObject.TOPIC_ENTITY_ROOT, m_sessionID));
            m_feature2DistributionAssociationRepositoryImpl = new Feature2DistributionAssociationRepositoryImpl(m_featureRepositoryImpl, m_distributionRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, Feature2DistributionAssociation.TOPIC_ENTITY_ROOT, m_sessionID));
            m_targetRepositoryImpl = new TargetRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, TargetObject.TOPIC_ENTITY_ROOT, m_sessionID));
            m_distribution2TargetAssociationRepositoryImpl = new Distribution2TargetAssociationRepositoryImpl(m_distributionRepositoryImpl, m_targetRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, Distribution2TargetAssociation.TOPIC_ENTITY_ROOT, m_sessionID));
            m_deploymentVersionRepositoryImpl = new DeploymentVersionRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, DeploymentVersionObject.TOPIC_ENTITY_ROOT, m_sessionID));
        }
        // first, register the artifact repository manually; it needs some special care.
        Component artifactRepoService = m_manager.createComponent()
            .setInterface(ArtifactRepository.class.getName(), m_sessionProps)
            .setImplementation(m_artifactRepositoryImpl)
            .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false))
            .add(m_manager.createServiceDependency().setService(ArtifactHelper.class).setRequired(false).setAutoConfig(false).setCallbacks(this, "addArtifactHelper", "removeArtifactHelper"));
        Dictionary topic = new Hashtable();
        topic.put(EventConstants.EVENT_TOPIC, new String[] {});
        topic.put(EventConstants.EVENT_FILTER, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")");
        Component artifactHandlerService = m_manager.createComponent()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(m_artifactRepositoryImpl);
        m_manager.add(artifactRepoService);
        m_manager.add(artifactHandlerService);

        m_services = new ArrayList<Component[]>();
        m_services.add(new Component[] {artifactRepoService, artifactHandlerService});

        // register all repositories are services. Keep the service objects around, we need them to pull the services later.
        m_services.add(registerRepository(Artifact2FeatureAssociationRepository.class, m_artifact2FeatureAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(ArtifactObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(FeatureObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(FeatureRepository.class, m_featureRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(Feature2DistributionAssociationRepository.class, m_feature2DistributionAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(FeatureObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(DistributionObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(DistributionRepository.class, m_distributionRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(Distribution2TargetAssociationRepository.class, m_distribution2TargetAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(DistributionObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(TargetObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(TargetRepository.class, m_targetRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(DeploymentVersionRepository.class, m_deploymentVersionRepositoryImpl, new String[] {}));

        // prepare the results.
        Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> result = new HashMap<Class<? extends ObjectRepository>, ObjectRepositoryImpl>();

        result.put(ArtifactRepository.class, m_artifactRepositoryImpl);
        result.put(Artifact2FeatureAssociationRepository.class, m_artifact2FeatureAssociationRepositoryImpl);
        result.put(FeatureRepository.class, m_featureRepositoryImpl);
        result.put(Feature2DistributionAssociationRepository.class, m_feature2DistributionAssociationRepositoryImpl);
        result.put(DistributionRepository.class, m_distributionRepositoryImpl);
        result.put(Distribution2TargetAssociationRepository.class, m_distribution2TargetAssociationRepositoryImpl);
        result.put(TargetRepository.class, m_targetRepositoryImpl);
        result.put(DeploymentVersionRepository.class, m_deploymentVersionRepositoryImpl);

        return result;
    }

    /**
     * Pulls all repository services; is used to make sure the repositories go away before the RepositoryAdmin does.
     */
    private void pullRepositories() {
        for (Component[] services : m_services) {
            for (Component service : services) {
                m_manager.remove(service);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RepositoryObject> Component[] registerRepository(Class<? extends ObjectRepository<T>> iface, ObjectRepositoryImpl<?, T> implementation, String[] topics) {
        Component repositoryService = m_manager.createComponent()
            .setInterface(iface.getName(), m_sessionProps)
            .setImplementation(implementation)
            .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false));
        Dictionary topic = new Hashtable();
        topic.put(EventConstants.EVENT_TOPIC, topics);
        topic.put(EventConstants.EVENT_FILTER, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")");
        Component handlerService = m_manager.createComponent()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(implementation);

        m_manager.add(repositoryService);
        m_manager.add(handlerService);
        return new Component[] {repositoryService, handlerService};
    }

    /**
     * Helper method for use in publishRepositories
     */
    private static String createPrivateObjectTopic(String entityRoot) {
        return RepositoryObject.PRIVATE_TOPIC_ROOT + entityRoot + RepositoryObject.TOPIC_ALL_SUFFIX;
    }

    public void checkout() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.checkout();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public void commit() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.commit();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public void flush() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.writeLocal();
                set.savePreferences();
            }
            m_changeNotifier.notifyChanged(TOPIC_FLUSHED_SUFFIX, null);
        }
    }

    public void revert() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.revert();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public boolean isCurrent() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            boolean result = true;
            for (RepositorySet set : m_repositorySets) {
                    result &= (set.isCurrent() || !set.writeAccess());
            }
            return result;
        }
    }

    public boolean isModified() {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                if (set.isModified()) {
                    return true;
                }
            }
            return false;
        }
    }

    public RepositoryAdminLoginContext createLoginContext(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User may not be null.");
        }
        return new RepositoryAdminLoginContextImpl(user, m_sessionID);
    }

    public void login(RepositoryAdminLoginContext context) throws IOException {
        if (!(context instanceof RepositoryAdminLoginContextImpl)) {
            throw new IllegalArgumentException("Only the RepositoryAdminLoginContext returned by createLoginContext can be used.");
        }

        RepositoryAdminLoginContextImpl impl = ((RepositoryAdminLoginContextImpl) context);
        RepositorySet[] repositorySets = getRepositorySets(impl);

        synchronized(m_lock) {
            // TODO I don't like this line, it should not be here...
            ((ArtifactRepositoryImpl) m_repositories.get(ArtifactRepository.class)).setObrBase(impl.getObrBase());
            login(impl.getUser(), repositorySets);
        }
    }

    /**
     * Helper method for login; also allows injection of custom RepositorySet objects for
     * testing purposes.
     * @throws IOException
     */
    private void login(User user, RepositorySet[] sets) throws IOException {
        synchronized(m_lock) {
            if (m_user != null) {
                throw new IllegalStateException("Another user is logged in.");
            }

            m_user = user;
            m_repositorySets = sets;
            for (RepositorySet set : m_repositorySets) {
                set.readLocal();
                set.loadPreferences();
            }
        }

        m_changeNotifier.notifyChanged(TOPIC_LOGIN_SUFFIX, null);
    }

    public void logout(boolean force) throws IOException {
        IOException exception = null;
        synchronized (m_lock) {
            ensureLogin();

            try {
                flush();
            }
            catch (IOException e) {
                if (!force) {
                    throw e;
                }
                else {
                    exception = e;
                }
            }

            for (RepositorySet set : m_repositorySets) {
                set.clearRepositories();
                set.unregisterHandler();
            }

            m_user = null;
            m_repositorySets = new RepositorySet[0];
        }
        m_changeNotifier.notifyChanged(TOPIC_LOGOUT_SUFFIX, null);
        if (exception != null) {
            throw exception;
        }
    }

    private boolean loggedIn() {
        return m_user != null;
    }

    /**
     * Helper method to make sure a user is logged in.
     * @throws IllegalStateException
     */
    private void ensureLogin() throws IllegalStateException {
        if (!loggedIn()) {
            throw new IllegalStateException("This operation requires a user to be logged in.");
        }
    }

    /**
     * Helper method, creates RepositorySets based on the Login context.
     */
    @SuppressWarnings("unchecked")
    private RepositorySet[] getRepositorySets(RepositoryAdminLoginContextImpl context) throws IOException {
        // First, some sanity checks on the list of descriptors.
        for (RepositorySetDescriptor rsd : context.getDescriptors()) {
            for (Class c : rsd.m_objectRepositories) {
                // Do we have an impl for each repository class?
                if (!m_repositories.containsKey(c)) {
                    throw new IllegalArgumentException(rsd.toString() + " references repository class " + c.getName() + " for which no implementation is available.");
                }
                // Do other sets have a reference to this same class?
                for (RepositorySetDescriptor other : context.getDescriptors()) {
                    if (other != rsd) {
                        for (Class otherC : other.m_objectRepositories) {
                            if (c.equals(otherC)) {
                                throw new IllegalArgumentException(rsd.toString() + " references repository class " + c.getName() + ", but so does " + other.toString());
                            }
                        }
                    }
                }
            }
        }

        RepositorySet[] result = new RepositorySet[context.getDescriptors().size()];

        /*
         * Create the lists of repositories and topics, and create and register
         * the sets with these.
         */
        for (int i = 0; i < result.length; i++) {
            RepositorySetDescriptor rsd = context.getDescriptors().get(i);
            ObjectRepositoryImpl[] impls = new ObjectRepositoryImpl[rsd.m_objectRepositories.length];
            String[] topics = new String[rsd.m_objectRepositories.length];
            for (int j = 0; j < impls.length; j++) {
                impls[j] = m_repositories.get(rsd.m_objectRepositories[j]);
                topics[j] = impls[j].getTopicAll(true);
            }
            result[i] = loadRepositorySet(context.getUser(), rsd, impls);
            result[i].registerHandler(m_context, m_sessionID, topics);
        }

        return result;
    }

    /**
     * Helper method for login.
     */
    private Preferences getRepositoryPrefs(Preferences userPrefs, URL location, String customer, String name) {
        // Note: we can only use the getAuthority part of the URL for indexing, because the full URL will contain
        // in the protocol part.
        Preferences repoPref = userPrefs.node(location.getAuthority() + location.getPath());
        Preferences customerPref = repoPref.node(customer);
        return customerPref.node(name);
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private File getFileFromPreferences(Preferences repositoryPrefs, String type) throws IOException {
        String sessionLocation = PREFS_LOCAL_FILE_LOCATION + m_sessionID;
        String directory = repositoryPrefs.get(sessionLocation, "");

        if ((directory == "") || !m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory()) {
            if (!m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory() && (directory != "")) {
                m_log.log(LogService.LOG_WARNING, "Directory '" + directory + "' should exist according to the preferences, but it does not.");
            }
            // The file did not exist, so create a new one.
            File directoryFile = null;
            try {
                File bundleDataDir = m_context.getDataFile(PREFS_LOCAL_FILE_ROOT);
                if (!bundleDataDir.isDirectory()) {
                    if (!bundleDataDir.mkdir()) {
                        throw new IOException("Error creating the local repository root directory.");
                    }
                }
                directoryFile = File.createTempFile("repo", "", bundleDataDir);
            }
            catch (IOException e) {
                // We cannot create the temp file? Then something is seriously wrong, so rethrow.
                throw e;
            }

            directoryFile.delete(); // No problem if this goes wrong, it just means it wasn't there yet.
            if (!directoryFile.mkdir()) {
                throw new IOException("Error creating the local repository storage directory.");
            }
            repositoryPrefs.put(sessionLocation, directoryFile.getName());
            return new File(directoryFile, type);
        }
        else {
            // Get the given file from that location.
            return m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory + "/" + type);
        }
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private BackupRepository getBackupFromPreferences(Preferences repositoryPrefs) throws IOException {
        File current = getFileFromPreferences(repositoryPrefs, PREFS_LOCAL_FILE_CURRENT);
        File backup = getFileFromPreferences(repositoryPrefs, PREFS_LOCAL_FILE_BACKUP);
        return new FilebasedBackupRepository(current, backup);
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private CachedRepository getCachedRepositoryFromPreferences(User user, Repository repository, Preferences repositoryPrefs) throws IOException {
        long mostRecentVersion = repositoryPrefs.getLong("version", CachedRepositoryImpl.UNCOMMITTED_VERSION);
        return new CachedRepositoryImpl(user, repository, getBackupFromPreferences(repositoryPrefs), mostRecentVersion);
    }

    /**
     * Helper method for login, which loads a set of repositories.
     * @param user A <code>User</code> object
     * @param rsd A RepositorySetDescriptor, defining the set to be created.
     * @param repos An array of <code>ObjectRepositoryImpl</code> which this set should manage. Each
     * @return The newly created repository set.
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public RepositorySet loadRepositorySet(User user, RepositorySetDescriptor rsd, ObjectRepositoryImpl[] repos) throws IOException {
        Repository repo = new RemoteRepository(rsd.m_location, rsd.m_customer, rsd.m_name);
        Preferences prefs = m_preferences.getUserPreferences(user.getName());
        Preferences repoPrefs = getRepositoryPrefs(prefs, rsd.m_location, rsd.m_customer, rsd.m_name);
        return new RepositorySet(m_changeNotifier, m_log, user, repoPrefs, repos, getCachedRepositoryFromPreferences(user, repo, repoPrefs), rsd.m_name, rsd.m_writeAccess);
    }

    public int getNumberWithWorkingState(Class<? extends RepositoryObject> clazz, WorkingState state) {
        int result = 0;
        synchronized (m_lock) {
            for (RepositorySet set : m_repositorySets) {
                result += set.getNumberWithWorkingState(clazz, state);
            }
        }
        return result;
    }

    public WorkingState getWorkingState(RepositoryObject object) {
        WorkingState result = null;
        synchronized (m_lock) {
            for (RepositorySet set : m_repositorySets) {
                result = set.getWorkingState(object);
                if (result != null) {
                    break;
                }
            }
        }
        return (result == null) ? WorkingState.Unchanged : result;
    }

    public void addArtifactHelper(ServiceReference ref, ArtifactHelper helper) {
        String mimetype = (String) ref.getProperty(ArtifactHelper.KEY_MIMETYPE);
        m_artifactRepositoryImpl.addHelper(mimetype, helper);
    }

    public synchronized void removeArtifactHelper(ServiceReference ref, ArtifactHelper helper) {
        String mimetype = (String) ref.getProperty(ArtifactHelper.KEY_MIMETYPE);
        m_artifactRepositoryImpl.removeHelper(mimetype, helper);
    }
}
