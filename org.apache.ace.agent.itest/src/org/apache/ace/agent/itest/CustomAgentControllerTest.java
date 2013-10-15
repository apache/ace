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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.UpdateHandler;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;

/**
 * Tests that we can create an agent with a completely custom controller.
 */
public class CustomAgentControllerTest extends BaseAgentTest {
    /**
     * Provides a simple implementation of {@link AgentUser} that always acknowledges a download and/or installation.
     */
    static class AcknowledgingAgentUser implements AgentUser {
        @Override
        public boolean downloadAvailableUpdate(UpdateType updateType, String agentId, Version from, Version to) {
            // Always proceed with a download...
            return true;
        }

        @Override
        public boolean installAvailableUpdate(UpdateType updateType, String agentId, Version from, Version to) {
            // Always proceed with the installation...
            return true;
        }
    }

    /**
     * Denotes a "user" of our agent that is monitoring our agent and able to respond to questions.
     */
    static interface AgentUser {
        /**
         * Asks the user whether or not to download an available update.
         * 
         * @param updateType
         *            the type of update to download, cannot be <code>null</code>;
         * @param agentId
         *            the identification of the agent that has an update available;
         * @param from
         *            the current installed version to upgrade from;
         * @param to
         *            the available version to upgrade to.
         * @return <code>true</code> if the update should be downloaded, <code>false</code> otherwise.
         */
        boolean downloadAvailableUpdate(UpdateType updateType, String agentId, Version from, Version to);

        /**
         * Asks the user whether or not to install an available update, after it has been downloaded.
         * 
         * @param updateType
         *            the type of update to install, cannot be <code>null</code>;
         * @param agentId
         *            the identification of the agent that has an update available;
         * @param from
         *            the current installed version to upgrade from;
         * @param to
         *            the available version to upgrade to.
         * @return <code>true</code> if the update should be installed, <code>false</code> otherwise.
         */
        boolean installAvailableUpdate(UpdateType updateType, String agentId, Version from, Version to);
    }

    /**
     * The actual custom controller as {@link Runnable} task, that simply loops and executes its tasks until notified to
     * stop.
     */
    class CustomController implements Runnable, DownloadHandle.DownloadProgressListener {
        private final CountDownLatch m_latch;
        private volatile boolean m_stop = false;

        public CustomController(CountDownLatch latch) {
            m_latch = latch;
        }

        @Override
        public void progress(long bytesRead, long totalBytes) {
            System.out.printf("Download progress: %d of %d bytes read...%n", bytesRead, totalBytes);
        }

        @Override
        public void run() {
            while (!m_stop) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException exception) {
                    // We're requested to stop...
                    break;
                }

                try {
                    // Send our feedback...
                    sendFeedbackToServer();
                }
                catch (Exception exception) {
                    System.out.printf("Feedback synchronization failed with %s.%n", exception.getMessage());
                    exception.printStackTrace(System.out);
                }

                try {
                    // Check for agent updates...
                    checkForUpdate(UpdateType.AGENT);
                }
                catch (Exception exception) {
                    System.out.printf("Agent update failed with %s.%n", exception.getMessage());
                    exception.printStackTrace(System.out);
                }

                try {
                    // Check for deployment updates...
                    checkForUpdate(UpdateType.DEPLOYMENT);
                }
                catch (Exception exception) {
                    System.out.printf("Deployment update failed with %s.%n", exception.getMessage());
                    exception.printStackTrace(System.out);
                }
            }
        }

        public void stop() {
            m_stop = true;
        }

        private void checkForUpdate(UpdateType updateType) throws Exception {
            UpdateHandler updateHandler = getUpdateHandler(updateType);

            Version installed = updateHandler.getInstalledVersion();
            Version available = updateHandler.getHighestAvailableVersion();
            if (installed != null && installed.compareTo(available) < 0) {
                // Update available, update the agent...
                if (!m_agentUser.downloadAvailableUpdate(updateType, getAgentId(), installed, available)) {
                    // No, we may not download this update now...
                    return;
                }

                System.out.printf("Downloading %s update (from v%s to v%s)...%n", updateType, installed, available);

                DownloadHandle downloadHandle = updateHandler.getDownloadHandle(available, false /* fixPackage */);

                Future<DownloadResult> future = downloadHandle.start(this);
                // Block until the download is complete...
                DownloadResult result = future.get();

                if (m_agentUser.installAvailableUpdate(updateType, getAgentId(), installed, available)) {
                    System.out.printf("Installing %s update (from v%s to v%s)...%n", updateType, installed, available);

                    // We've confirmation that we can install this update...
                    updateHandler.install(result.getInputStream());

                    m_latch.countDown();
                }
            }
        }

        private String getAgentId() {
            return m_control.getAgentId();
        }

        private UpdateHandler getUpdateHandler(UpdateType updateType) {
            UpdateHandler updateHandler;
            if (UpdateType.AGENT == updateType) {
                updateHandler = m_control.getAgentUpdateHandler();
            }
            else {
                updateHandler = m_control.getDeploymentHandler();
            }
            return updateHandler;
        }

        private void sendFeedbackToServer() throws IOException {
            FeedbackHandler feedbackHandler = m_control.getFeedbackHandler();
            Set<String> channelNames = feedbackHandler.getChannelNames();
            for (String channelName : channelNames) {
                FeedbackChannel channel = feedbackHandler.getChannel(channelName);

                System.out.printf("Synchronizing feedback of %s with server...%n", channelName);

                channel.sendFeedback();

                m_latch.countDown();
            }
        }
    }

    static class TestDeploymentServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final Map<String, TestPackage> m_packages = new HashMap<String, TestPackage>();
        private final String m_agentId;

        public TestDeploymentServlet(String agentId, TestPackage... testPackages) {
            m_agentId = agentId;

            for (TestPackage testPackage : testPackages) {
                m_packages.put(testPackage.getVersion().toString(), testPackage);
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String pathInfo = req.getPathInfo();

            if (pathInfo.startsWith("/auditlog/query")) {
                resp.setContentType("text/plain");
                PrintWriter writer = resp.getWriter();
                writer.println(req.getParameter("tid") + "," + req.getParameter("logid") + ",0-10");
                writer.close();
            }
            else if (pathInfo.startsWith("/deployment/")) {
                String pathinfoTail = pathInfo.replaceFirst("/deployment/" + m_agentId + "/versions/?", "");
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
            else if (pathInfo.startsWith("/agent/")) {
                String tail = pathInfo.replaceFirst("/agent/" + m_agentId + "/org.apache.ace.agent/versions/", "");
                if ("".equals(tail)) {
                    sendVersions(resp);
                }
            }
            else {
                resp.setContentLength(0);
                resp.setStatus(HttpServletResponse.SC_OK);
            }
            resp.flushBuffer();
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String pathInfo = request.getPathInfo();
            if (pathInfo.startsWith("/auditlog/")) {
                InputStream is = request.getInputStream();
                while (is.read() != -1) {
                }
                is.close();
            }
            response.setContentType("text/plain");
            response.flushBuffer();
        }

        @Override
        protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("text/plain");
            response.flushBuffer();
        }

        private void sendPackage(TestPackage dpackage, HttpServletRequest req, HttpServletResponse resp) throws IOException {
            InputStream is = null;
            OutputStream os = null;
            try {
                is = new FileInputStream(dpackage.getFile());
                os = resp.getOutputStream();

                int read;
                byte[] buffer = new byte[4096];
                do {
                    read = is.read(buffer);
                    if (read >= 0) {
                        os.write(buffer, 0, read);
                    }
                }
                while (read >= 0);
            }
            finally {
                if (is != null) {
                    is.close();
                }
                if (os != null) {
                    os.close();
                }
            }
        }

        private void sendVersions(HttpServletResponse resp) throws IOException {
            PrintWriter writer = resp.getWriter();
            for (String version : m_packages.keySet()) {
                writer.println(version);
            }
            writer.close();

            resp.setContentType("text/plain");
            resp.setStatus(200);
            resp.flushBuffer();
        }
    }

    /**
     * Denotes the kind of update.
     */
    static enum UpdateType {
        AGENT, DEPLOYMENT;
    }

    private static final Version V1_0_0 = Version.parseVersion("1.0.0");
    private static final String TEST_BUNDLE_NAME_PREFIX = "test.bundle";
    private static final String AGENT_ID = "defaultTargetID";

    // Injected by Felix DM...
    private volatile HttpService m_http;
    private volatile AgentControl m_control;
    private volatile AgentUser m_agentUser;

    /**
     * Tests that we can provide a custom controller implementation based on the following use-case:
     * <p>
     * ...
     * </p>
     */
    public void testCustomController() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);

        CustomController controller = new CustomController(latch);

        Thread thread = new Thread(controller);
        thread.start();

        boolean success = latch.await(30, TimeUnit.SECONDS);

        waitForInstalledVersion(V1_0_0);

        controller.stop();
        thread.join();

        assertTrue("Use case not completed!", success);
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        m_control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_DISABLED, "true");

        TestBundle bundle1v1 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat("1"), V1_0_0);
        TestPackage package1 = new TestPackage(AGENT_ID, V1_0_0, bundle1v1);

        TestDeploymentServlet servlet = new TestDeploymentServlet(AGENT_ID, package1);

        String url = String.format("http://localhost:%d/", TestConstants.PORT);
        NetUtils.waitForURL(url, 404, 10000);

        m_http.registerServlet("/", servlet, null, null);

        NetUtils.waitForURL(url, 200, 10000);
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        m_bundleContext.registerService(AgentUser.class.getName(), new AcknowledgingAgentUser(), null);
    }

    @Override
    protected void doTearDown() throws Exception {
        // Remove all provisioned components...
        m_dependencyManager.clear();

        m_http.unregister("/");

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
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(AgentControl.class).setRequired(true))
                .add(createServiceDependency().setService(AgentUser.class).setRequired(true))
        };
    }

    private void waitForInstalledVersion(Version version) throws Exception {
        DeploymentHandler deploymentHandler = m_control.getDeploymentHandler();

        int timeout = 100;
        while (!deploymentHandler.getInstalledVersion().equals(version)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                fail("Timed out while waiting for deployment " + version);
            }
        }
    }
}
