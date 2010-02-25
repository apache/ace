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
package org.apache.ace.test.deployment;

import static org.apache.ace.test.utils.TestUtils.INTEGRATION;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.discovery.property.constants.DiscoveryConstants;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.deployment.BundleStreamGenerator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentException;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class DeploymentIntegrationTest implements BundleListener, EventHandler {
    public static final String HOST = "localhost";
    public static final String GWID = "gw-id";
    public static final long POLL_INTERVAL = 1000;
    private static Object instance;
    private volatile ConfigurationAdmin m_config;
    private volatile DeploymentAdmin m_deployment;
    private volatile BundleContext m_context;
    private volatile File m_tempDir;
    private final Semaphore m_semaphore = new Semaphore(0);
    Map<Integer, List<Bundle>> m_events = new HashMap<Integer, List<Bundle>>();
    private ServiceRegistration m_deploymentAdminProxyRegistration;

    public DeploymentIntegrationTest() throws IOException {
        synchronized (DeploymentIntegrationTest.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }

    @BeforeTest(alwaysRun = true)
    public void setUp() throws IOException {
        m_tempDir = File.createTempFile("test", "");
        m_tempDir.delete();
        m_tempDir.mkdir();
    }

    @AfterTest(alwaysRun = true)
    public void tearDown() {
        deleteDirOrFile(m_tempDir);
    }

    private void deleteDirOrFile(File root) {
        if (root.isDirectory()) {
            for (File file : root.listFiles()) {
                deleteDirOrFile(file);
            }
        }
        root.delete();
    }

    @Factory
    public Object[] createInstance() {
        synchronized (DeploymentIntegrationTest.class) {
            return new Object[] { instance };
        }
    }

    @Test(groups = { INTEGRATION })
    public void deployVersionSeries() throws Exception {
        Bundle[] start = m_context.getBundles();

        // Test deploy initial version 1.0.0 with 3 bundles in version 1.0.0
        String[] versions = new String[] { "bundle1", "bundle2", "bundle3" };
        generateBundles(createVersion("1.0.0"), versions, 0, versions.length, "1.0.0");
        executeTest();
        // start + versions bundles may be present
        assertState(start, m_context.getBundles(), versions);
        assert m_events.get(BundleEvent.STARTED).size() == versions.length : "Received unexpected amount of starting events.";
        assert m_events.get(BundleEvent.STOPPED) == null : "Received unexpected amount of stopping events";
        m_events.clear();

        // Test correct presence of deployment packages in deployment admin
        assert m_deployment.listDeploymentPackages().length == 1 : "Deployment admin reports unexpected number of deployment packages";
        assert m_deployment.getDeploymentPackage(GWID) != null : "Deployment admin did not return the expected deployment package";
        Bundle[] bundles = m_context.getBundles();
        Bundle bundle = null;
        for (int i = 0; i < bundles.length; i++) {
            if("bundle1".equals(bundles[i].getSymbolicName())) {
                bundle = bundles[i];
                break;
            }
        }
        assert m_deployment.getDeploymentPackage(bundle) != null : "Deployment admin did not return the expected deployment package";

        // Test deploy a version 1.1.0 on top of the previous 1.0.0 with one new bundle and one updated to version 1.1.0 (i.e., two fix-package bundles)
        versions = new String[] { "bundle1", "bundle2", "bundle3", "bundle4" };
        File version = createVersion("1.1.0");
        generateBundle(new File(version, "0.jar"), versions[0], "1.1.0");
        generateBundles(version, versions, 1, versions.length, "1.0.0");
        executeTest();
        // start + versions bundles may be present
        assertState(start, m_context.getBundles(), versions);
        assert m_events.get(BundleEvent.UPDATED).size() == 1 : "Received unexpected amount of updated events.";
        assert m_events.get(BundleEvent.UPDATED).get(0).getSymbolicName().equals(versions[0]) : "Received unexpected update event.";
        assert m_events.get(BundleEvent.STOPPED).size() == versions.length - 1 : "Received unexpected amount of stopped events.";
        assert m_events.get(BundleEvent.STARTED).size() == versions.length : "Received unexpected amount of started events: expected " + versions.length + ", received " + m_events.get(BundleEvent.STARTED).size() + ".";
        m_events.clear();

        // Test to deploy an empty version 2.0.0, but break the stream which should cancel the deployment
        createVersion("2.0.0");
        executeTestWithFailingStream();
        m_events.clear();

        // Test to deploy an empty version 2.0.0 which should remove all the previously installed bundles
        executeTest();

        // only start bundles may be present
        assertState(start, m_context.getBundles(), new String[0]);
        assert m_events.get(BundleEvent.INSTALLED) == null : "Received unexpected amount of installed events.";
        assert m_events.get(BundleEvent.STARTED) == null : "Received unexpected amount of starting events.";
        assert m_events.get(BundleEvent.UNINSTALLED).size() == versions.length : "Received unexpected amount of uninstalled events: " + m_events.get(BundleEvent.UNINSTALLED).size() + " instead of " + versions.length;
        assert m_events.get(BundleEvent.STOPPED).size() == versions.length : "Received unexpected amount of stopped events: " + m_events.get(BundleEvent.STOPPED).size() + " instead of " + versions.length;
        m_events.clear();

    }

    public void bundleChanged(BundleEvent event) {
        synchronized (m_events) {
            if (!m_events.containsKey(Integer.valueOf(event.getType()))) {
                m_events.put(Integer.valueOf(event.getType()), new ArrayList<Bundle>());
            }
            m_events.get(Integer.valueOf(event.getType())).add(event.getBundle());
        }
    }

    public void handleEvent(Event event) {
        System.out.println("Event: " + event);
        m_semaphore.release();
    }

    private void generateBundles(File dir, String[] versions, int off, int len, String version) throws Exception {
        for (int i = off; i < len; i++) {
            generateBundle(new File(dir, i + ".jar"), versions[i], version);
        }
    }

    private void assertState(Bundle[] start, Bundle[] current, String[] versions) {
        assert (start.length + versions.length) == current.length : "System has " + (((start.length + versions.length) < current.length) ? "more" : "less") + " bundes then expected: expected " + (start.length + versions.length) + ", found " + current.length;
        for (int i = 0; i < start.length; i++) {
            assert current[i].getSymbolicName().equals(start[i].getSymbolicName()) : "Bundle names do not match: " + current[i].getSymbolicName() + " v.s. " + start[i];
        }
        List<String> index = Arrays.asList(versions);
        for (int i = start.length; i < current.length; i++) {
            assert index.contains(current[i].getSymbolicName()) : "Bundle names do not match: " + current[i].getSymbolicName();
        }
    }

    private File createVersion(String version) {
        File versionFile = new File(new File(m_tempDir, GWID), version);
        versionFile.mkdirs();
        return versionFile;
    }

    @SuppressWarnings("serial")
    private void executeTest() throws IOException, InterruptedException {
        m_context.addBundleListener(this);
        ServiceRegistration reg = m_context.registerService(EventHandler.class.getName(), this, new Properties() {
            {
                put(EventConstants.EVENT_TOPIC, "org/osgi/service/deployment/COMPLETE");
                put(EventConstants.EVENT_FILTER, "(successful=true)");
            }
        });
        configureGateway();
        configureServer();
        assert m_semaphore.tryAcquire(8, TimeUnit.SECONDS) : "Timed out while waiting for deployment to complete.";
        unconfigureServer();
        unconfigureGateway();
        reg.unregister();
        m_context.removeBundleListener(this);
    }

    private void executeTestWithFailingStream() throws IOException, InterruptedException {
        m_context.addBundleListener(this);
        registerDeploymentAdminProxy(new FailingDeploymentAdmin(m_deployment, 50));
        configureGateway();
        configureServer();
        assert m_semaphore.tryAcquire(8, TimeUnit.SECONDS) : "Timed out while waiting for deployment to abort.";
        unconfigureServer();
        unconfigureGateway();
        unregisterDeploymentAdminProxy();
        m_context.removeBundleListener(this);
    }

    /**
     * Input stream wrapper that creates an input stream that breaks after N bytes. When it
     * breaks, it will throw an IO exception and sends a notification to the semaphore that
     * allows the overall test to continue.
     */
    private class BrokenInputStream extends InputStream {
        private final InputStream m_normalStream;
        private int m_bytesUntilBreakdown;
        private boolean m_isBroken;

        public BrokenInputStream(InputStream normalStream, int bytesUntilBreakdown) {
            m_normalStream = normalStream;
            m_bytesUntilBreakdown = bytesUntilBreakdown;
        }

        private synchronized void breakStream() throws IOException {
            if (!m_isBroken) {
                m_isBroken = true;

                // release the semaphore to continue the test
                m_semaphore.release();
            }
            throw new IOException("Stream broken.");
        }

        @Override
        public int read() throws IOException {
            if (m_bytesUntilBreakdown-- < 1) {
                breakStream();
            }
            return m_normalStream.read();
        }

        @Override
        public void close() throws IOException {
            m_normalStream.close();
            breakStream();
        }

    }

    /**
     * Wrapper around the deployment admin that will fail once, after N bytes.
     */
    private class FailingDeploymentAdmin implements DeploymentAdmin {
        private final DeploymentAdmin m_deploymentAdmin;
        private boolean m_wasBroken;
        private final int m_failAfterBytes;

        public FailingDeploymentAdmin(DeploymentAdmin deploymentAdmin, int failAfterBytes) {
            m_deploymentAdmin = deploymentAdmin;
            m_failAfterBytes = failAfterBytes;
        }

        public boolean cancel() {
            return m_deploymentAdmin.cancel();
        }

        public DeploymentPackage getDeploymentPackage(String symbName) {
            return m_deploymentAdmin.getDeploymentPackage(symbName);
        }

        public DeploymentPackage getDeploymentPackage(Bundle bundle) {
            return m_deploymentAdmin.getDeploymentPackage(bundle);
        }

        public DeploymentPackage installDeploymentPackage(InputStream in) throws DeploymentException {
            synchronized (this) {
                if (!m_wasBroken) {
                    m_wasBroken = true;
                    in = new BrokenInputStream(in, m_failAfterBytes);
                }
            }
            return m_deploymentAdmin.installDeploymentPackage(in);
        }

        public DeploymentPackage[] listDeploymentPackages() {
            return m_deploymentAdmin.listDeploymentPackages();
        }
    }

    private void configureGateway() throws IOException {
        // configure discovery bundle
        setProperty(DiscoveryConstants.DISCOVERY_PID, new Object[][] { { DiscoveryConstants.DISCOVERY_URL_KEY, "http://" + HOST + ":" + TestConstants.PORT } });
        // configure identification bundle
        setProperty(IdentificationConstants.IDENTIFICATION_PID, new Object[][] { { IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, GWID } });
        // configure scheduler
        setProperty(SchedulerConstants.SCHEDULER_PID, new Object[][] {
            { "org.apache.ace.gateway.auditlog.task.AuditLogSyncTask", POLL_INTERVAL },
            { "org.apache.ace.deployment.task.DeploymentUpdateTask", POLL_INTERVAL }
            });
    }

    private void unconfigureGateway() throws IOException {
        m_config.getConfiguration(DiscoveryConstants.DISCOVERY_PID, null).delete();
        m_config.getConfiguration(IdentificationConstants.IDENTIFICATION_PID, null).delete();
        m_config.getConfiguration(SchedulerConstants.SCHEDULER_PID, null).delete();
    }

    private void unconfigureServer() throws IOException {
        m_config.getConfiguration("org.apache.ace.deployment.servlet", null).delete();
        m_config.getConfiguration("org.apache.ace.deployment.provider.filebased", null).delete();
    }

    private void configureServer() throws IOException {
        // configure data bundle
        setProperty(org.apache.ace.deployment.servlet.Activator.PID, new Object[][] { { HttpConstants.ENDPOINT, "/deployment" } });
        // configure file based backend
        setProperty(org.apache.ace.deployment.provider.filebased.Activator.PID, new Object[][] { { "BaseDirectoryName", m_tempDir.getAbsolutePath() } });
    }

    @SuppressWarnings("unchecked")
    private void setProperty(String pid, Object[][] props) throws IOException {
        Configuration configuration = m_config.getConfiguration(pid, null);
        Dictionary dictionary = configuration.getProperties();
        if (dictionary == null) {
            dictionary = new Hashtable();
        }
        for (Object[] pair : props) {
            dictionary.put(pair[0], pair[1]);
        }
        configuration.update(dictionary);
    }

    private ArtifactData generateBundle(File file, String symbolicName, String version) throws Exception {
        ArtifactData bundle = new ArtifactDataImpl(file.getName(), symbolicName, version, file.toURI().toURL(), false);
        BundleStreamGenerator.generateBundle(bundle);
        return bundle;
    }

    private void registerDeploymentAdminProxy(DeploymentAdmin proxy) {
        Properties props = new Properties();
        props.put(org.osgi.framework.Constants.SERVICE_RANKING, 1);
        m_deploymentAdminProxyRegistration = m_context.registerService(DeploymentAdmin.class.getName(), proxy, props);
    }

    private void unregisterDeploymentAdminProxy() {
        m_deploymentAdminProxyRegistration.unregister();
    }
}
