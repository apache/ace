package org.apache.ace.agent.itest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.builder.DeploymentPackageBuilder;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;

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
public abstract class BaseAgentControllerTest extends IntegrationTestBase {
    /**
     * Denotes a "user" of our agent that is monitoring our agent and able to respond to questions.
     */
    public static interface AgentUser {
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
     * Denotes the kind of update.
     */
    public static enum UpdateType {
        AGENT, DEPLOYMENT;
    }

    /**
     * Provides a simple implementation of {@link AgentUser} that always acknowledges a download and/or installation.
     */
    public static class AcknowledgingAgentUser implements AgentUser {
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
     * Stub servlet that acts as an ACE server for our agent. Does only the bare minimum with respect to a complete
     * server.
     */
    public static class StubDeploymentServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final Map<String, TestPackage> m_packages = new HashMap<>();
        private final String m_agentId;

        public StubDeploymentServlet(String agentId, TestPackage... testPackages) {
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
        protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("text/plain");
            response.flushBuffer();
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

    protected static class TestBundle {
        private final File m_file;

        public TestBundle(String name, Version version, String... headers) throws Exception {
            m_file = createBundle(name, version, headers);
        }

        public File getFile() {
            return m_file;
        }
    }

    protected static class TestPackage {
        private final String m_name;
        private final Version m_version;
        private final File m_file;

        public TestPackage(String name, Version version, TestBundle... bundles) throws Exception {
            m_name = name;
            m_version = version;

            File[] files = new File[bundles.length];
            for (int i = 0; i < bundles.length; i++) {
                files[i] = bundles[i].getFile();
            }
            m_file = createPackage(m_name, m_version, files);
        }

        public File getFile() {
            return m_file;
        }

        public String getName() {
            return m_name;
        }

        public Version getVersion() {
            return m_version;
        }
    }

    protected static final Version V1_0_0 = Version.parseVersion("1.0.0");

    private static final String TEST_BUNDLE_NAME_PREFIX = "test.bundle";
    private static final String AGENT_ID = "customizedAgent";

    private final String m_controllerName;
    private final String m_bundleSuffix;
    protected final Version m_dpVersion;

    private BundleActivator m_agentActivator;

    // Injected by Felix DM...
    protected volatile HttpService m_http;
    protected volatile AgentControl m_agentControl;
    protected volatile AgentUser m_agentUser;

    /**
     * Creates a new BaseAgentControllerTest instance.
     */
    public BaseAgentControllerTest(String controllerName, String bundleSuffix, String version) {
        m_controllerName = controllerName;
        m_bundleSuffix = bundleSuffix;
        m_dpVersion = new Version(version);
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        TestBundle bundle1v1 = new TestBundle(TEST_BUNDLE_NAME_PREFIX.concat(m_bundleSuffix), V1_0_0);
        TestPackage package1 = new TestPackage(AGENT_ID, m_dpVersion, bundle1v1);

        StubDeploymentServlet servlet = new StubDeploymentServlet(AGENT_ID, package1);

        String url = String.format("http://localhost:%d/", TestConstants.PORT);
        NetUtils.waitForURL_NotFound(url);

        m_http.registerServlet("/", servlet, null, null);

        NetUtils.waitForURL(url);

        // Tell our agent what controller to use, in this case, we simply disable the controller as we want to invoke
        // everything externally from the AgentControl service...
        System.setProperty(AgentConstants.CONFIG_CONTROLLER_CLASS, m_controllerName);
        System.setProperty(AgentConstants.CONFIG_IDENTIFICATION_AGENTID, AGENT_ID);
        System.setProperty(AgentConstants.CONFIG_LOGGING_LEVEL, "DEBUG");
        System.setProperty(AgentConstants.CONFIG_DISCOVERY_SERVERURLS, url);

        // We start the bundle activator ourselves (to avoid weird circularities and timing issues)...
        startAgentBundle();
    }

    protected void configureAgent(ConfigurationHandler handler, String... configuration) {
        Map<String, String> config = new HashMap<>();
        for (int i = 0; i < configuration.length; i += 2) {
            config.put(configuration[i], configuration[i + 1]);
        }
        handler.putAll(config);
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        final String agentActivatorName = "org.apache.ace.agent.impl.Activator";

        Bundle bundle = FrameworkUtil.getBundle(getClass());

        Class<?> activatorClass = bundle.loadClass(agentActivatorName);
        assertNotNull("Failed to load agent activator class (" + agentActivatorName + ")!", activatorClass);

        m_agentActivator = (BundleActivator) activatorClass.newInstance();
    }

    @Override
    protected void doTearDown() throws Exception {
        // Make sure other tests are not influenced by this!
        System.clearProperty(AgentConstants.CONFIG_CONTROLLER_CLASS);
        System.clearProperty(AgentConstants.CONFIG_IDENTIFICATION_AGENTID);
        System.clearProperty(AgentConstants.CONFIG_LOGGING_LEVEL);

        // We also should stop our agent bundle ourselves...
        stopAgentBundle();

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

        // Cleanup the package area of the DeploymentAdmin...
        File packagesArea = m_bundleContext.getDataFile("packages");
        cleanDir(packagesArea);
    }

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(AgentControl.class).setRequired(false))
                .add(createServiceDependency().setService(AgentUser.class).setRequired(false))
        };
    }

    /**
     * @throws Exception
     *             in case starting the agent failed.
     */
    protected void startAgentBundle() throws Exception {
        m_agentActivator.start(m_bundleContext);
    }

    /**
     * @throws Exception
     *             in case stopping the agent failed.
     */
    protected void stopAgentBundle() throws Exception {
        m_agentActivator.stop(m_bundleContext);
    }

    protected void waitForInstalledVersion(DeploymentHandler deploymentHandler, Version version) throws Exception {
        int timeout = 100;
        while (!deploymentHandler.getInstalledVersion().equals(version)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                fail("Timed out while waiting for deployment " + version);
            }
        }
    }

    protected static File createBundle(String bsn, Version version, String... headers) throws Exception {
        return FileUtils.createEmptyBundle(bsn, version, headers);
    }

    protected static File createPackage(String name, Version version, File... bundles) throws Exception {
        DeploymentPackageBuilder builder = DeploymentPackageBuilder.createDeploymentPackage(name, version.toString());

        OutputStream fos = null;
        try {
            for (File bundle : bundles) {
                builder.addBundle(bundle.toURI().toURL());
            }

            File file = File.createTempFile("testpackage", ".jar");
            file.deleteOnExit();

            fos = new FileOutputStream(file);
            builder.generate(fos);

            return file;
        }
        finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    private void cleanDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalStateException();
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                cleanDir(file);
            }
            file.delete();
        }
        dir.delete();
    }
}
