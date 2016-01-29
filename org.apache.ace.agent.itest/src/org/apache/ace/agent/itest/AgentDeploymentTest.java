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
package org.apache.ace.agent.itest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.LoggingHandler.Levels;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;

/**
 * Tests updating the management agent. In fact it tests different failure paths first, and finally gets to update the
 * agent. The tests it does are:
 * <ul>
 * <li>Try to update to a corrupt bundle (with some random garbage injected in the JAR file).</li>
 * <li>Try to update to a bundle that does not resolve because of some impossible import package statement.</li>
 * <li>Try to update to a bundle that does resolve, but does not start because of a non-existing bundle activator.</li>
 * <li>Update to a new version of the agent (actually, it's the same bundle, but with a different version.</li>
 * </ul>
 */
public class AgentDeploymentTest extends BaseAgentTest {

    private enum Failure {
        EMPTY_STREAM, CORRUPT_STREAM, ABORT_STREAM, VERSIONS_RETRY_AFTER, DEPLOYMENT_RETRY_AFTER, CONTENT_RANGE
    }

    private static class TestAuditlogServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        // FIXME Ignoring auditlog.. but why do we get and empty send if we set range to high?

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            PrintWriter writer = response.getWriter();
            writer.println(request.getParameter("tid") + "," + request.getParameter("logid") + ",0-10");
            writer.close();
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            InputStream is = request.getInputStream();
            while (is.read() != -1) {
            }
            is.close();
            response.setContentType("text/plain");
        }
    }

    private static class TestDeploymentServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private static final String BACKOFF_TIME = "1";

        private final Map<String, TestPackage> m_packages = new HashMap<>();
        private final String m_agentId;
        private Failure m_failure;

        public TestDeploymentServlet(String agentId) {
            m_agentId = agentId;
        }

        public synchronized void addPackage(TestPackage testPackage) {
            m_packages.put(testPackage.getVersion().toString(), testPackage);
        }

        public synchronized void reset() {
            m_failure = null;
            m_packages.clear();
        }

        public synchronized void setFailure(Failure failure) {
            m_failure = failure;
        }

        @Override
        protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String pathinfoTail = req.getPathInfo().replaceFirst("/" + m_agentId + "/versions/?", "");
            if (pathinfoTail.equals("")) {
                sendVersions(resp);
            }
            else {
                TestPackage dpackage = m_packages.get(pathinfoTail);
                if (dpackage == null) {
                    throw new IllegalStateException("Test error! Should never happen... " + pathinfoTail);
                }
                sendPackage(dpackage, req, resp);
            }
        }

        @Override
        protected synchronized void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String pathinfoTail = req.getPathInfo().replaceFirst("/" + m_agentId + "/versions/?", "");
            if (pathinfoTail.equals("")) {
                sendVersions(resp);
            }
            else {
                TestPackage dpackage = m_packages.get(pathinfoTail);
                if (dpackage == null) {
                    throw new IllegalStateException("Test error! Should never happen... " + pathinfoTail);
                }
                int offset = -2;
                resp.addIntHeader("X-ACE-DPSize", offset + dpackage.getVersion().getMajor());
                resp.flushBuffer();
            }
        }

        private void sendPackage(TestPackage dpackage, HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (m_failure == Failure.DEPLOYMENT_RETRY_AFTER) {
                resp.addHeader("Retry-After", BACKOFF_TIME);
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Simulated server overload");
                m_failure = null;
                return;
            }

            final long fileLength = dpackage.getFile().length();
            final long middle = fileLength / 2;

            long start = 0L;
            long end = fileLength;

            if (m_failure == Failure.CONTENT_RANGE) {
                String rangeHdr = req.getHeader("Range");
                if (rangeHdr != null && rangeHdr.startsWith("bytes=")) {
                    // Continuation...
                    String[] range = rangeHdr.substring(6).split("-");

                    start = Long.parseLong(range[0]);
                }
                else {
                    // Initial chuck...
                    end = fileLength / 2;
                }

                if (start == end) {
                    // Invalid...
                    resp.addHeader("Content-Range", String.format("bytes */%d", fileLength));
                    resp.setStatus(416); // content range not satisfiable...
                    return;
                }

                resp.addHeader("Content-Range", String.format("bytes %d-%d/%d", start, end - 1, fileLength));
                resp.setStatus(206); // partial
            }

            RandomAccessFile raf = null;
            OutputStream os = null;
            try {
                raf = new RandomAccessFile(dpackage.getFile(), "r");
                os = resp.getOutputStream();

                if (m_failure == Failure.EMPTY_STREAM) {
                    return;
                }

                if (m_failure == Failure.CORRUPT_STREAM) {
                    os.write("garbage".getBytes());
                }

                if (m_failure == Failure.CONTENT_RANGE) {
                    raf.seek(start);
                }

                int b;
                int count = 0;
                while (count < (end - start) && (b = raf.read()) != -1) {
                    os.write(b);
                    if (count++ == middle && m_failure == Failure.ABORT_STREAM) {
                        break;
                    }
                }
            }
            finally {
                raf.close();
                if (os != null) {
                    os.close();
                }
            }
        }

        private void sendVersions(HttpServletResponse resp) throws IOException {
            if (m_failure == Failure.VERSIONS_RETRY_AFTER) {
                resp.addHeader("Retry-After", BACKOFF_TIME);
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Simulated server overload");
                m_failure = null;
                return;
            }
            PrintWriter writer = resp.getWriter();
            for (String version : m_packages.keySet()) {
                writer.println(version);
            }
            writer.close();
            resp.setContentType("text/plain");
            resp.setStatus(200);
        }
    }

    private static class TestEventListener implements EventListener {
        private static boolean matches(Map<String, String> source, Map<String, String> target) {
            for (Map.Entry<String, String> sourceEntry : source.entrySet()) {
                String sourceKey = sourceEntry.getKey();
                String sourceValue = sourceEntry.getValue();

                if (!target.containsKey(sourceKey)) {
                    return false;
                }
                String targetValue = target.get(sourceKey);
                if (!sourceValue.equals(targetValue)) {
                    return false;
                }
            }

            return true;
        }

        private final Map<String, List<Map<String, String>>> m_topics = new HashMap<>();

        public boolean containsTopic(String topic) {
            synchronized (m_topics) {
                return m_topics.containsKey(topic);
            }
        }

        public boolean containsTopic(String topic, Map<String, String> expectedProperties) {
            synchronized (m_topics) {
                List<Map<String, String>> payloads = m_topics.get(topic);
                if (payloads == null || payloads.isEmpty()) {
                    return expectedProperties.isEmpty();
                }
                for (Map<String, String> payload : payloads) {
                    if (matches(expectedProperties, payload)) {
                        return true;
                    }
                }
                return false;
            }
        }

        public Map<String, List<Map<String, String>>> getTopics() {
            Map<String, List<Map<String, String>>> result;
            synchronized (m_topics) {
                result = new HashMap<>(m_topics);
            }
            return result;
        }

        @Override
        public void handle(String topic, Map<String, String> payload) {
            if (LOGLEVEL == Levels.DEBUG) {
                System.out.printf("Handling event: %s => %s.%n", topic, payload);
            }

            synchronized (m_topics) {
                List<Map<String, String>> payloads = m_topics.get(topic);
                if (payloads == null) {
                    payloads = new ArrayList<>();
                    m_topics.put(topic, payloads);
                }
                payloads.add(payload);
            }
        }
    }

    private static class TestUpdateServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setStatus(200);
        }
    }

    private static final String AGENT_INSTALLATION_START = "agent/defaultController/installation/START";
    private static final String AGENT_INSTALLATION_COMPLETE = "agent/defaultController/installation/COMPLETE";

    private static final String AGENT_ID = "007";
    private static final String TEST_BUNDLE_NAME_PREFIX = "test.bundle";
    private static final Levels LOGLEVEL = Levels.INFO;

    private static final Version V1_0_0 = Version.parseVersion("1.0.0");
    private static final Version V2_0_0 = Version.parseVersion("2.0.0");
    private static final Version V3_0_0 = Version.parseVersion("3.0.0");
    private static final Version V4_0_0 = Version.parseVersion("4.0.0");
    private static final Version V5_0_0 = Version.parseVersion("5.0.0");
    private static final Version V6_0_0 = Version.parseVersion("6.0.0");

    private volatile TestDeploymentServlet m_servlet;
    private volatile HttpService m_http;
    private volatile TestEventListener m_listener;

    private TestPackage m_package1;
    private TestPackage m_package2;
    private TestPackage m_package3;
    private TestPackage m_package4;
    private TestPackage m_package5;
    private TestPackage m_package6;
    private TestPackage m_package7;

    /**
     * Test case for ACE-323: when a version of a DP was downloaded correctly, but did not install correctly, we should
     * not keep trying, unless a newer version of that DP is available.
     */
    public void testFailedDeploymentWithoutRetrying() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectSuccessfulDeployment(m_package1, null);

        // Try to install a DP that fails due to an aborted stream...
        expectFailedDeployment(m_package2, Failure.ABORT_STREAM);
        waitForInstalledVersion(V1_0_0);

        // The failed DP should not be installed again...
        TimeUnit.SECONDS.sleep(2); // sleep a little while to show the retry in the log...

        // If we install a newer version, it should succeed...
        expectSuccessfulDeployment(m_package6, null);

        TimeUnit.SECONDS.sleep(2); // sleep a little while to receive async events..

        // Check our event log, should contain all handled events...
        Map<String, List<Map<String, String>>> topics = m_listener.getTopics();

        List<Map<String, String>> events = topics.get(AGENT_INSTALLATION_START);
        // should contain exactly three different elements...
        assertEquals(events.toString(), 3, events.size());

        events = topics.get(AGENT_INSTALLATION_COMPLETE);
        // should contain exactly three different elements...
        assertEquals(events.toString(), 3, events.size());
    }

    /**
     * Tests that we can install upgrades for an earlier installed DP.
     */
    public void testGetSizeEstimateForDeploymentPackage() throws Exception {
        AgentControl control = getService(AgentControl.class);

        Map<String, String> props = createAgentConfiguration(false /* useStreaming */, 1000 /* secs */);

        ConfigurationHandler configurationHandler = control.getConfigurationHandler();
        configurationHandler.putAll(props);

        // Allow configuration to propagate...
        Thread.sleep(100L);

        synchronized (m_servlet) {
            m_servlet.reset();
        }

        waitForInstalledVersion(Version.emptyVersion);

        synchronized (m_servlet) {
            m_servlet.addPackage(m_package1);
            m_servlet.addPackage(m_package2);
            m_servlet.addPackage(m_package6);
        }

        DeploymentHandler deploymentHandler = control.getDeploymentHandler();
        // the size is (major-version # - 2)...
        assertEquals(4, deploymentHandler.getSize(V6_0_0, false));
        assertEquals(0, deploymentHandler.getSize(V2_0_0, false));
        assertEquals(-1, deploymentHandler.getSize(V1_0_0, false));
    }

    /**
     * Tests that we can install upgrades for an earlier installed DP.
     */
    public void testInstallUpgradeDeploymentPackage() throws Exception {
        setupAgentForNonStreamingDeployment();

        // Try to install a DP that fails at bundle-starting due to a non-existing class, but this does not revert the
        // installation of the DP itself...
        expectSuccessfulDeployment(m_package5, null);

        // If we install a newer version, it should succeed...
        expectSuccessfulDeployment(m_package6, null);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectSuccessfulDeployment(m_package6, null);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_AbortedStream() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectFailedDeployment(m_package5, Failure.ABORT_STREAM);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     * <p>
     * This test simulates a DP that is already downloaded, but not yet installed as reported in ACE-413.
     * </p>
     */
    public void testNonStreamingDeployment_ChunkedContentAlreadyCompletelyDownloaded() throws Exception {
        setupAgentForNonStreamingDeployment();

        // Simulate that the DP is already downloaded...
        simulateDPDownloadComplete(m_package6);

        expectSuccessfulDeployment(m_package6, Failure.CONTENT_RANGE);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_ChunkedContentRange() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectSuccessfulDeployment(m_package6, Failure.CONTENT_RANGE);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_CorruptStream() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectFailedDeployment(m_package4, Failure.CORRUPT_STREAM);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_DeploymentRetryAfter() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectSuccessfulDeployment(m_package2, Failure.DEPLOYMENT_RETRY_AFTER);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_EmptyStream() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectFailedDeployment(m_package3, Failure.EMPTY_STREAM);
    }

    /**
     * ACE-451: Tests the deployment of an invalid "non-streamed" deployment packages, which should cause the
     * installation to be aborted.
     */
    public void testNonStreamingDeployment_InvalidDeploymentPackage() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectFailedDeployment(m_package7, null);
    }

    /**
     * Tests the deployment of "non-streamed" deployment packages in various situations.
     */
    public void testNonStreamingDeployment_VersionsRetryAfter() throws Exception {
        setupAgentForNonStreamingDeployment();

        expectSuccessfulDeployment(m_package1, Failure.VERSIONS_RETRY_AFTER);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment() throws Exception {
        setupAgentForStreamingDeployment();

        expectSuccessfulDeployment(m_package6, null);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_AbortStream() throws Exception {
        setupAgentForStreamingDeployment();

        expectFailedDeployment(m_package5, Failure.ABORT_STREAM);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_ChunkedContentRange() throws Exception {
        setupAgentForStreamingDeployment();

        expectSuccessfulDeployment(m_package1, Failure.CONTENT_RANGE);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_CorruptStream() throws Exception {
        setupAgentForStreamingDeployment();

        expectFailedDeployment(m_package4, Failure.CORRUPT_STREAM);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_DeploymentRetryAfter() throws Exception {
        setupAgentForStreamingDeployment();

        expectSuccessfulDeployment(m_package2, Failure.DEPLOYMENT_RETRY_AFTER);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_EmptyStream() throws Exception {
        setupAgentForStreamingDeployment();

        expectFailedDeployment(m_package3, Failure.EMPTY_STREAM);
    }

    /**
     * ACE-451: Tests the deployment of an invalid "streamed" deployment packages, which should cause the installation
     * to be aborted.
     */
    public void testStreamingDeployment_InvalidDeploymentPackage() throws Exception {
        setupAgentForStreamingDeployment();

        expectFailedDeployment(m_package7, null);
    }

    /**
     * Tests the deployment of "streamed" deployment packages in various situations.
     */
    public void testStreamingDeployment_VersionsRetryAfter() throws Exception {
        setupAgentForStreamingDeployment();

        expectSuccessfulDeployment(m_package1, Failure.VERSIONS_RETRY_AFTER);
    }

    /**
     * Tests the deployment of "streamed" deployment packages simulating an "unstable" connection.
     */
    public void testStreamingDeploymentWithUnstableConnection() throws Exception {
        setupAgentForStreamingDeployment();

        expectSuccessfulDeployment(m_package1, null);

        expectFailedDeployment(m_package6, Failure.EMPTY_STREAM);
        waitForInstalledVersion(V1_0_0);

        expectFailedDeployment(m_package6, Failure.CORRUPT_STREAM);
        waitForInstalledVersion(V1_0_0);

        expectFailedDeployment(m_package6, Failure.ABORT_STREAM);
        waitForInstalledVersion(V1_0_0);

        expectFailedDeployment(m_package6, Failure.EMPTY_STREAM);
        waitForInstalledVersion(V1_0_0);

        expectFailedDeployment(m_package6, null);
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        TestBundle bundle1v1 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("1"), V1_0_0);
        TestBundle bundle1v2 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("1"), V2_0_0);
        TestBundle bundle2v1 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("2"), V1_0_0);
        TestBundle bundle2v2 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("2"), V2_0_0);
        TestBundle bundle3v1 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("3"), V1_0_0, Constants.BUNDLE_ACTIVATOR, "no.such.Class");
        TestBundle bundle3v2 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("3"), V2_0_0);

        m_package1 = new TestPackage(AGENT_ID, V1_0_0, bundle1v1);
        m_package2 = new TestPackage(AGENT_ID, V2_0_0, bundle1v2);
        m_package3 = new TestPackage(AGENT_ID, V3_0_0, bundle1v2, bundle2v1);
        m_package4 = new TestPackage(AGENT_ID, V4_0_0, bundle1v2, bundle2v2);
        m_package5 = new TestPackage(AGENT_ID, V5_0_0, bundle1v2, bundle2v2, bundle3v1);
        m_package6 = new TestPackage(AGENT_ID, V6_0_0, bundle1v2, bundle2v2, bundle3v2);
        // This leads to an *incorrect* DP, as it contains two bundles with the same BSN...
        m_package7 = new TestPackage(AGENT_ID, V1_0_0, bundle1v1, bundle1v2);

        m_servlet = new TestDeploymentServlet(AGENT_ID);

        m_http.registerServlet("/deployment", m_servlet, null, null);
        m_http.registerServlet("/agent", new TestUpdateServlet(), null, null);
        m_http.registerServlet("/auditlog", new TestAuditlogServlet(), null, null);
    }

    @Override
    protected void doTearDown() throws Exception {
        // Remove all provisioned components...
        m_dependencyManager.clear();

        m_http.unregister("/deployment");
        m_http.unregister("/agent");
        m_http.unregister("/auditlog");

        // Force an uninstall of all remaining test bundles...
        for (Bundle bundle : m_bundleContext.getBundles()) {
            String bsn = bundle.getSymbolicName();
            if (bsn.startsWith(TEST_BUNDLE_NAME_PREFIX)) {
                bundle.uninstall();
            }
        }

        resetAgentBundleState();
    }

    @Override
    protected Component[] getDependencies() {
        m_listener = new TestEventListener();
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true)),
            createComponent()
                .setInterface(EventListener.class.getName(), null)
                .setImplementation(m_listener)
        };
    }

    private Map<String, String> createAgentConfiguration(boolean useStreaming, int syncInterval) {
        Map<String, String> props = new HashMap<>();
        props.put(AgentConstants.CONFIG_DISCOVERY_SERVERURLS, String.format("http://localhost:%d/", TestConstants.PORT));
        props.put(AgentConstants.CONFIG_IDENTIFICATION_AGENTID, AGENT_ID);
        props.put(AgentConstants.CONFIG_LOGGING_LEVEL, LOGLEVEL.name());
        props.put(AgentConstants.CONFIG_CONTROLLER_STREAMING, Boolean.toString(useStreaming));
        props.put(AgentConstants.CONFIG_CONTROLLER_SYNCDELAY, "1");
        props.put(AgentConstants.CONFIG_CONTROLLER_SYNCINTERVAL, Integer.toString(syncInterval));
        props.put(AgentConstants.CONFIG_CONTROLLER_RETRIES, "2");
        return props;
    }

    private void deployPackage(TestPackage dpackage, Failure failure) {
        synchronized (m_servlet) {
            m_servlet.setFailure(failure);
            m_servlet.addPackage(dpackage);
        }
    }

    private void expectFailedDeployment(TestPackage dpackage, Failure failure) throws Exception {
        deployPackage(dpackage, failure);

        waitForEventReceived(AGENT_INSTALLATION_START);
        waitForEventReceived(AGENT_INSTALLATION_COMPLETE, "successful", "false");
    }

    private void expectSuccessfulDeployment(TestPackage dpackage, Failure failure) throws Exception {
        deployPackage(dpackage, failure);

        waitForEventReceived(AGENT_INSTALLATION_START);
        waitForEventReceived(AGENT_INSTALLATION_COMPLETE, "successful", "true");

        waitForInstalledVersion(dpackage.getVersion());
    }

    private void setupAgentForNonStreamingDeployment() throws Exception {
        AgentControl control = getService(AgentControl.class);

        Map<String, String> props = createAgentConfiguration(false /* useStreaming */, 1 /* sec */);

        ConfigurationHandler configurationHandler = control.getConfigurationHandler();
        configurationHandler.putAll(props);

        synchronized (m_servlet) {
            m_servlet.reset();
        }

        waitForInstalledVersion(Version.emptyVersion);
    }

    private void setupAgentForStreamingDeployment() throws Exception {
        AgentControl control = getService(AgentControl.class);

        Map<String, String> props = createAgentConfiguration(true /* useStreaming */, 1 /* sec */);

        ConfigurationHandler configurationHandler = control.getConfigurationHandler();
        configurationHandler.putAll(props);

        waitForInstalledVersion(Version.emptyVersion);
    }

    /**
     * Simulates a DP that is already completely downloaded.
     * 
     * @param _package
     *            the test package to simulate a download for, cannot be <code>null</code>.
     * @throws IOException
     *             in case of I/O problems.
     */
    private void simulateDPDownloadComplete(TestPackage _package) throws IOException {
        Bundle agentBundle = FrameworkUtil.getBundle(AgentConstants.class);
        assertNotNull(agentBundle);
        assertFalse(agentBundle.getBundleId() == m_bundleContext.getBundle().getBundleId());

        // The filename used for DP is the encoded URL...
        String dpFilename = String.format("http%%3A%%2F%%2Flocalhost%%3A%d%%2Fdeployment%%2F%s%%2Fversions%%2F%s", TestConstants.PORT, AGENT_ID, _package.getVersion());
        File dpFile = new File(agentBundle.getBundleContext().getDataFile(""), dpFilename);

        FileUtils.copy(_package.getFile(), dpFile);
    }

    private void waitForEventReceived(String topic) throws Exception {
        int timeout = 10000;
        while (!m_listener.containsTopic(topic)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                fail("Timed out while waiting for event " + topic);
            }
        }
    }

    private void waitForEventReceived(String topic, String... properties) throws Exception {
        Map<String, String> props = new HashMap<>();
        for (int i = 0; i < properties.length; i += 2) {
            props.put(properties[i], properties[i + 1]);
        }

        int timeout = 100;
        while (!m_listener.containsTopic(topic, props)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                fail("Timed out while waiting for event " + topic);
            }
        }
    }

    private void waitForInstalledVersion(Version version) throws Exception {
        ServiceReference<AgentControl> reference = m_bundleContext.getServiceReference(AgentControl.class);

        try {
            AgentControl control = m_bundleContext.getService(reference);
            DeploymentHandler deploymentHandler = control.getDeploymentHandler();

            int timeout = 100;
            while (!deploymentHandler.getInstalledVersion().equals(version)) {
                Thread.sleep(100);
                if (timeout-- <= 0) {
                    fail("Timed out while waiting for deployment " + version);
                }
            }
        }
        finally {
            m_bundleContext.ungetService(reference);
        }
    }
}
