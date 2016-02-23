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
package org.apache.ace.it.repositoryadmin;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.obr.storage.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.ComponentState;
import org.apache.felix.dm.ComponentStateListener;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.osgi.util.tracker.ServiceTracker;

public abstract class BaseRepositoryAdminTest extends IntegrationTestBase {

    protected static final String TEST_USER_NAME = "testUser";

    final class MockUser implements User {
        private final String m_name;

        public MockUser() {
            m_name = String.format("user-%s", Long.toHexString(System.nanoTime()));
        }

        public Dictionary<Object, Object> getCredentials() {
            return new Properties();
        }

        public String getName() {
            return m_name;
        }

        public Dictionary<Object, Object> getProperties() {
            return new Properties();
        }

        public int getType() {
            return 0;
        }

        public boolean hasCredential(String arg0, Object arg1) {
            return false;
        }
    }

    protected static final String ENDPOINT_NAME = "/repository";
    protected static final String HOST = "http://localhost:" + TestConstants.PORT;

    protected URL m_endpoint;
    protected URL m_obrURL;

    /* All injected by dependency manager */
    protected volatile ConfigurationAdmin m_configAdmin;
    protected volatile RepositoryAdmin m_repositoryAdmin;
    protected volatile ArtifactRepository m_artifactRepository;
    protected volatile Artifact2FeatureAssociationRepository m_artifact2featureRepository;
    protected volatile FeatureRepository m_featureRepository;
    protected volatile Feature2DistributionAssociationRepository m_feature2distributionRepository;
    protected volatile DistributionRepository m_distributionRepository;
    protected volatile Distribution2TargetAssociationRepository m_distribution2targetRepository;
    protected volatile TargetRepository m_targetRepository;
    protected volatile DeploymentVersionRepository m_deploymentVersionRepository;
    protected volatile StatefulTargetRepository m_statefulTargetRepository;
    protected volatile LogStore m_auditLogStore;

    protected final void addObr(String endpoint, String fileLocation) throws IOException, InterruptedException {
        String baseURL = String.format("http://localhost:%d%s/", TestConstants.PORT, endpoint);

        m_obrURL = new URL(baseURL);

        configure("org.apache.ace.client.repository", "obrlocation", m_obrURL.toExternalForm());

        configure("org.apache.ace.obr.storage.file", OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);

        // Wait for the endpoint to respond.
        URL repoURL = new URL(baseURL + "index.xml");

        assertTrue("The OBR servlet does not seem to be responding well!", NetUtils.waitForURL(repoURL));
    }

    /* Configure a new repository instance */
    protected final void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException,
        InterruptedException, InvalidSyntaxException {
        // Publish configuration for a repository instance
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, customer);
        props.put(RepositoryConstants.REPOSITORY_NAME, name);
        props.put(RepositoryConstants.REPOSITORY_MASTER, String.valueOf(isMaster));
        props.put("factory.instance.pid", instanceName);
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        ServiceTracker<?, ?> tracker = new ServiceTracker<>(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
        tracker.open();

        config.update(props);

        if (tracker.waitForService(5000) == null) {
            throw new IOException("Did not get notified about new repository becoming available in time.");
        }

        tracker.close();
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        // remove all repositories, in case a test case does not reach it's cleanup section due to an exception
        removeAllRepositories();
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        m_endpoint = new URL(HOST + ENDPOINT_NAME);

        getService(SessionFactory.class).createSession("test-session-ID", null);

        configure("org.apache.ace.log.server.store.filebased", "MaxEvents", "0");

        configureFactory("org.apache.ace.log.server.store.factory", "name", "auditlog");

        configure("org.apache.ace.http.context", "authentication.enabled", "false");
    }

    protected final void cleanUp() throws InvalidSyntaxException, InterruptedException {
        // Simply remove all objects in the repository.
        clearRepository(m_artifactRepository);
        clearResourceProcessors(m_artifactRepository);
        clearRepository(m_artifact2featureRepository);
        clearRepository(m_feature2distributionRepository);
        clearRepository(m_distribution2targetRepository);
        clearRepository(m_artifactRepository);
        clearRepository(m_featureRepository);
        clearRepository(m_distributionRepository);
        clearRepository(m_targetRepository);
        clearRepository(m_deploymentVersionRepository);
        m_statefulTargetRepository.refresh();
        try {
            m_repositoryAdmin.logout(true);
        }
        catch (Exception ioe) {
            // ioe.printStackTrace(System.out);
        }
    }

    protected ArtifactObject createBasicArtifactObject(String name, String mimetype, String processorPID)
        throws InterruptedException {
        Map<String, String> attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        attr.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        attr.put(ArtifactObject.KEY_URL, "http://" + name);
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, processorPID);
        Map<String, String> tags = new HashMap<>();

        return m_artifactRepository.create(attr, tags);
    }

    protected ArtifactObject createBasicBundleObject(String symbolicName) {
        return createBasicBundleObject(symbolicName, null, null);
    }

    protected ArtifactObject createBasicBundleObject(String symbolicName, String version, String processorPID) {
        return createBasicBundleObject(symbolicName, version, processorPID, null);
    }

    protected ArtifactObject createBasicBundleObject(String symbolicName, String version, String processorPID, String size) {
        Map<String, String> attr = new HashMap<>();
        attr.put(BundleHelper.KEY_SYMBOLICNAME, symbolicName);
        attr.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        attr.put(ArtifactObject.KEY_URL, "http://" + symbolicName + "-" + ((version == null) ? "null" : version));

        if (version != null) {
            attr.put(BundleHelper.KEY_VERSION, version);
        }
        if (processorPID != null) {
            attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, processorPID);
        }
        if (size != null) {
            attr.put(ArtifactObject.KEY_SIZE, size);
        }

        Map<String, String> tags = new HashMap<>();
        return m_artifactRepository.create(attr, tags);
    }

    protected DistributionObject createBasicDistributionObject(String name) {
        Map<String, String> attr = new HashMap<>();
        attr.put(DistributionObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<>();

        return m_distributionRepository.create(attr, tags);
    }

    protected FeatureObject createBasicFeatureObject(String name) {
        Map<String, String> attr = new HashMap<>();
        attr.put(FeatureObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<>();

        return m_featureRepository.create(attr, tags);
    }

    protected TargetObject createBasicTargetObject(String id) {
        Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, id);
        Map<String, String> tags = new HashMap<>();

        return m_targetRepository.create(attr, tags);
    }

    /**
     * @return a {@link User} with {@link #TEST_USER_NAME} as user name, can be <code>null</code>.
     */
    protected final User createTestUser() {
        return createUser(TEST_USER_NAME);
    }

    /**
     * @param name
     *            the name of the user to create, cannot be <code>null</code>;
     * @return a {@link User} with the given name, or <code>null</code> in case no such user could be created.
     */
    protected final User createUser(String name) {
        UserAdmin useradmin = getService(UserAdmin.class);
        User user = (User) useradmin.createRole(name, Role.USER);
        if (user == null) {
            user = useradmin.getUser("username", name);
        }
        else {
            user.getProperties().put("username", name);
        }
        return user;
    }

    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(RepositoryAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Artifact2FeatureAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(FeatureRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Feature2DistributionAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(DistributionRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Distribution2TargetAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(TargetRepository.class).setRequired(true))
                .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
                .add(createServiceDependency().setService(StatefulTargetRepository.class).setRequired(true))
                .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
                .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
        };
    }

    protected <T> T runAndWaitForEvent(Callable<T> callable, final boolean debug, final String... topicList) throws Exception {
        return runAndWaitForEvent(callable, debug, null, topicList);
    }

    protected <T> T runAndWaitForEvent(Callable<T> callable, final boolean debug, final List<Event> events, final String... topicList) throws Exception {
        Dictionary<String, Object> topics = new Hashtable<>();
        topics.put(EventConstants.EVENT_TOPIC, topicList);

        final CopyOnWriteArrayList<String> waitingForTopic = new CopyOnWriteArrayList<>(Arrays.asList(topicList));
        final CountDownLatch topicLatch = new CountDownLatch(topicList.length);
        final CountDownLatch startLatch = new CountDownLatch(1);

        Component comp = m_dependencyManager.createComponent()
            .setInterface(EventHandler.class.getName(), topics)
            .setImplementation(new EventHandler() {
                @Override
                public void handleEvent(Event event) {
                    if (debug) {
                        System.err.println("Received event: " + event.getTopic());
                    }
                    if (waitingForTopic.remove(event.getTopic())) {
                        if (events != null) {
                            events.add(event);
                        }
                        if (debug) {
                            System.err.println("Event was expected.");
                        }
                        topicLatch.countDown();
                    }
                }
            });
        comp.add(new ComponentStateListener() {
            @Override
            public void changed(Component component, ComponentState state) {
                if (state == ComponentState.TRACKING_OPTIONAL) {
                    startLatch.countDown();
                }
            }
        });

        if (debug) {
            System.err.printf("Waiting for events: %s.%n", Arrays.toString(topicList));
        }

        m_dependencyManager.add(comp);

        try {
            assertTrue(startLatch.await(1500, TimeUnit.MILLISECONDS));

            T result = null;
            // XXX this is dodgy, I know, but currently a workaround for some spurious failing itests...
            int tries = 10;
            while (tries-- > 0) {
                try {
                    result = callable.call();
                    break;
                }
                catch (Exception exception) {
                    if (exception instanceof ConnectException) {
                        // Restart it...
                        if (tries == 0) {
                            throw exception;
                        }
                    }
                    else {
                        // Rethrow it...
                        throw exception;
                    }
                }
            }

            boolean r = topicLatch.await(15000, TimeUnit.MILLISECONDS);
            if (!r && debug) {
                System.err.println("EVENT NOTIFICATION FAILED!!!");
            }
            assertTrue("We expect the event within a reasonable timeout.", r);

            return result;
        }
        finally {
            m_dependencyManager.remove(comp);
        }
    }

    @Override
    protected void doTearDown() throws Exception {
        try {
            m_repositoryAdmin.logout(true);
        }
        catch (RuntimeException e) {
            // Ignore...
        }

        try {
            cleanUp();
        }
        catch (Exception e) {
            // Ignore...
        }

        try {
            removeAllRepositories();
        }
        catch (IOException e) {
            // Ignore...
        }
    }

    private <T extends RepositoryObject> void clearRepository(ObjectRepository<T> rep) {
        for (T entity : rep.get()) {
            try {
                rep.remove(entity);
            }
            catch (RuntimeException e) {
                // Ignore; try to recover...
            }
        }
        assertEquals("Something went wrong clearing the repository.", 0, rep.get().size());
    }

    private void clearResourceProcessors(ArtifactRepository rep) {
        for (ArtifactObject entity : rep.getResourceProcessors()) {
            try {
                rep.remove(entity);
            }
            catch (RuntimeException e) {
                // Ignore; try to recover...
            }
        }
        assertEquals("Something went wrong clearing the repository.", 0, rep.get().size());
    }

    private void removeAllRepositories() throws IOException, InvalidSyntaxException, InterruptedException {
        final Configuration[] configs = m_configAdmin.listConfigurations("(factory.instance.pid=*)");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);

            ServiceTracker<Repository, Repository> tracker = new ServiceTracker<Repository, Repository>(m_bundleContext, Repository.class, null) {
                @Override
                public void removedService(ServiceReference<Repository> reference, Repository service) {
                    super.removedService(reference, service);
                    // config.length times two because the service tracker also sees added events for each instance
                    if (size() == 0) {
                        sem.release();
                    }
                }
            };
            tracker.open();

            for (int i = 0; i < configs.length; i++) {
                configs[i].delete();
            }

            if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new IOException("Not all instances were removed in time.");
            }
            tracker.close();
        }
    }
}
