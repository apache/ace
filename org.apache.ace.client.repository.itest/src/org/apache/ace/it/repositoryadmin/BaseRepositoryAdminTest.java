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

import static org.apache.ace.client.repository.RepositoryObject.PRIVATE_TOPIC_ROOT;
import static org.apache.ace.client.repository.RepositoryObject.PUBLIC_TOPIC_ROOT;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ALL;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
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
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.useradmin.User;
import org.osgi.util.tracker.ServiceTracker;

public abstract class BaseRepositoryAdminTest extends IntegrationTestBase implements EventHandler {

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

    protected static final String ENDPOINT_NAME = "/AdminRepTest";
    protected static final String HOST = "http://localhost:" + TestConstants.PORT;

    protected volatile ConfigurationAdmin m_configAdmin; /* Injected by dependency manager */
    protected volatile RepositoryAdmin m_repositoryAdmin; /* Injected by dependency manager */
    protected volatile ArtifactRepository m_artifactRepository; /* Injected by dependency manager */
    protected volatile Artifact2FeatureAssociationRepository m_artifact2featureRepository; /* Injected by dependency manager */
    protected volatile FeatureRepository m_featureRepository; /* Injected by dependency manager */
    protected volatile Feature2DistributionAssociationRepository m_feature2distributionRepository; /* Injected by dependency manager */
    protected volatile DistributionRepository m_distributionRepository; /* Injected by dependency manager */
    protected volatile Distribution2TargetAssociationRepository m_distribution2targetRepository; /* Injected by dependency manager */
    protected volatile TargetRepository m_targetRepository; /* Injected by dependency manager */
    protected volatile DeploymentVersionRepository m_deploymentVersionRepository; /* Injected by dependency manager */
    protected volatile StatefulTargetRepository m_statefulTargetRepository; /* Injected by dependency manager */
    protected volatile LogStore m_auditLogStore; /* Injected by dependency manager */
    protected volatile List<String> m_waitingForTopic = Collections.synchronizedList(new ArrayList<String>());
    protected volatile Semaphore m_semaphore;
    
    protected URL m_endpoint;
    
    private volatile boolean m_runAndWaitDebug = false;

    public void handleEvent(Event event) {
        if (m_runAndWaitDebug) {
            System.err.println("Received event: " + event.getTopic());
        }
        if (m_waitingForTopic.remove(event.getTopic())) {
            if (m_runAndWaitDebug) {
                System.err.println("Event was expected.");
            }
            if ((m_semaphore != null) && m_waitingForTopic.isEmpty()) {
                m_semaphore.release();
                m_runAndWaitDebug = false;
            }
        }
    }
    
    protected final void addObr(String endpoint, String fileLocation) throws IOException, InterruptedException {
        configure("org.apache.ace.obr.servlet", "OBRInstance", "singleOBRServlet", "org.apache.ace.server.servlet.endpoint", endpoint, "authentication.enabled", "false");
        configure("org.apache.ace.obr.storage.file", "OBRInstance", "singleOBRStore", OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);

        // Wait for the endpoint to respond.
        // TODO below there is a similar url that does put a slash between port and endpoint, why?
        URL url = new URL("http://localhost:" + TestConstants.PORT + endpoint + "/repository.xml");
        int response = ((HttpURLConnection) url.openConnection()).getResponseCode();
        int tries = 0;
        while ((response != 200) && (tries++ < 50)) {
            response = ((HttpURLConnection) url.openConnection()).getResponseCode();
            Thread.sleep(100); // If we get interrupted, there will be a good reason for it.
        }
        if (tries == 50) {
            throw new IOException("The OBR servlet does not seem to be responding well. Last response code: " + response);
        }
    }
    
    /* Configure a new repository instance */
    protected final void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException,
        InterruptedException, InvalidSyntaxException {
        // Publish configuration for a repository instance
        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, customer);
        props.put(RepositoryConstants.REPOSITORY_NAME, name);
        props.put(RepositoryConstants.REPOSITORY_MASTER, String.valueOf(isMaster));
        props.put("factory.instance.pid", instanceName);
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        ServiceTracker tracker = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
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
        configureFactory("org.apache.ace.log.server.store.factory",
            "name", "auditlog", "authentication.enabled", "false");
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
//            ioe.printStackTrace(System.out);
        }
    }
    
    protected ArtifactObject createBasicArtifactObject(String name, String mimetype, String processorPID)
        throws InterruptedException {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        attr.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        attr.put(ArtifactObject.KEY_URL, "http://" + name);
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, processorPID);
        Map<String, String> tags = new HashMap<String, String>();

        return m_artifactRepository.create(attr, tags);
    }

    protected ArtifactObject createBasicBundleObject(String symbolicName) {
        return createBasicBundleObject(symbolicName, null, null);
    }

    protected ArtifactObject createBasicBundleObject(String symbolicName, String version, String processorPID) {
        return createBasicBundleObject(symbolicName, version, processorPID, null);
    }
    
    protected ArtifactObject createBasicBundleObject(String symbolicName, String version, String processorPID, String size) {
        Map<String, String> attr = new HashMap<String, String>();
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

        Map<String, String> tags = new HashMap<String, String>();
        return m_artifactRepository.create(attr, tags);
    }

    protected DistributionObject createBasicDistributionObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DistributionObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_distributionRepository.create(attr, tags);
    }

    protected FeatureObject createBasicFeatureObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(FeatureObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_featureRepository.create(attr, tags);
    }

    protected TargetObject createBasicTargetObject(String id) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, id);
        Map<String, String> tags = new HashMap<String, String>();

        return m_targetRepository.create(attr, tags);
    }

    protected void deleteObr(String endpoint) throws IOException, InvalidSyntaxException, InterruptedException {
        // This is a little ugly: we cannot just delete the configuration, since that will result in a
        // sharing violation between this bundle and the servlet bundle. In stead, we make the servlet
        // use an invalid endpoint.
        Properties propsServlet = new Properties();
        propsServlet.put(HttpConstants.ENDPOINT, endpoint + "invalid");
        propsServlet.put("OBRInstance", "singleOBRServlet");
        Configuration configServlet = m_configAdmin.getConfiguration("org.apache.ace.obr.servlet");
        configServlet.update(propsServlet);

        URL url = new URL("http://localhost:" + TestConstants.PORT + "/" + endpoint + "/repository.xml");
        int response = ((HttpURLConnection) url.openConnection()).getResponseCode();
        int tries = 0;
        while ((response != 404) && (tries < 50)) {
            Thread.sleep(100); // If we get interrupted, there will be a good reason for it.
            response = ((HttpURLConnection) url.openConnection()).getResponseCode();
            tries++;
        }
        if (tries == 50) {
            throw new IOException("The OBR servlet does not want to go away. Last response code: " + response);
        }
    }

    protected Component[] getDependencies() {
        Dictionary<String, Object> topics = new Hashtable<String, Object>();
        topics.put(EventConstants.EVENT_TOPIC, new String[] { PUBLIC_TOPIC_ROOT + "*",
            PRIVATE_TOPIC_ROOT + "*",
            RepositoryAdmin.PUBLIC_TOPIC_ROOT + "*",
            RepositoryAdmin.PRIVATE_TOPIC_ROOT + "*",
            TOPIC_ALL });
        return new Component[] {
            createComponent()
                .setInterface(EventHandler.class.getName(), topics)
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

    protected <T> T runAndWaitForEvent(Callable<T> callable, boolean debug, String... topic) throws Exception {
        m_runAndWaitDebug = debug;
        T result = null;
        m_waitingForTopic.clear();
        m_waitingForTopic.addAll(Arrays.asList(topic));
        m_semaphore = new Semaphore(0);
        result = callable.call();
        assertTrue("We expect the event within a reasonable timeout.", m_semaphore.tryAcquire(15000, TimeUnit.MILLISECONDS));
        m_semaphore = null;
        return result;
    }
    
    protected void startRepositoryService() throws IOException {
        // configure the (replication)repository servlets
        configure("org.apache.ace.repository.servlet.RepositoryServlet", HttpConstants.ENDPOINT,
            ENDPOINT_NAME, "authentication.enabled", "false");
    }

    @Override
    protected void doTearDown() throws Exception {
    	try {
			m_repositoryAdmin.logout(true);
		} catch (RuntimeException e) {
			// Ignore...
		}

    	try {
			cleanUp();
		} catch (Exception e) {
			// Ignore...
		}
    	
    	try {
			removeAllRepositories();
		} catch (IOException e) {
			// Ignore...
		}
    }

    private <T extends RepositoryObject> void clearRepository(ObjectRepository<T> rep) {
        for (T entity : rep.get()) {
            try {
				rep.remove(entity);
			} catch (RuntimeException e) {
				// Ignore; try to recover...
			}
        }
        assertEquals("Something went wrong clearing the repository.", 0, rep.get().size());
    }

    private void clearResourceProcessors(ArtifactRepository rep) {
        for (ArtifactObject entity : rep.getResourceProcessors()) {
            try {
				rep.remove(entity);
			} catch (RuntimeException e) {
				// Ignore; try to recover...
			}
        }
        assertEquals("Something went wrong clearing the repository.", 0, rep.get().size());
    }

    private void removeAllRepositories() throws IOException, InvalidSyntaxException, InterruptedException {
        final Configuration[] configs = m_configAdmin.listConfigurations("(factory.instance.pid=*)");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);

            ServiceTracker tracker =
                new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(" + Constants.OBJECTCLASS + "="
                    + Repository.class.getName() + ")"), null) {
                    @Override
                    public void removedService(ServiceReference reference, Object service) {
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
