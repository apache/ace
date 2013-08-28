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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.builder.DeploymentPackageBuilder;
import org.apache.felix.dm.Component;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;

import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;

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
        EMPTY_STREAM, CORRUPT_STREAM, ABORT_STREAM, VERSIONS_RETRY_AFTER, DEPLOYMENT_RETRY_AFTER

    }

    private volatile TestDeploymentServlet m_servlet;
    private volatile HttpService m_http;
    private volatile TestEventListener m_listener;

    private final Version version1 = Version.parseVersion("1.0.0");
    private final Version version2 = Version.parseVersion("2.0.0");
    private final Version version3 = Version.parseVersion("3.0.0");
    private final Version version4 = Version.parseVersion("4.0.0");
    private final Version version5 = Version.parseVersion("5.0.0");
    private final Version version6 = Version.parseVersion("6.0.0");

    private TestPackage m_package1;
    private TestPackage m_package2;
    private TestPackage m_package3;
    private TestPackage m_package4;
    private TestPackage m_package5;
    private TestPackage m_package6;

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

    @Override
    public void configureAdditionalServices() throws Exception {

        TestBundle bundle1v1 = new TestBundle("bundle1", version1);
        TestBundle bundle1v2 = new TestBundle("bundle1", version2);
        TestBundle bundle2v1 = new TestBundle("bundle2", version1);
        TestBundle bundle2v2 = new TestBundle("bundle2", version2);

        TestBundle bundle3v1 = new TestBundle("bundle3", version1, Constants.BUNDLE_ACTIVATOR, "no.Such.Class");
        TestBundle bundle3v2 = new TestBundle("bundle3", version2);

        m_package1 = new TestPackage("007", version1, bundle1v1);
        m_package2 = new TestPackage("007", version2, bundle1v2);
        m_package3 = new TestPackage("007", version3, bundle1v2, bundle2v1);
        m_package4 = new TestPackage("007", version4, bundle1v2, bundle2v2);
        m_package5 = new TestPackage("007", version5, bundle1v2, bundle2v2, bundle3v1);
        m_package6 = new TestPackage("007", version6, bundle1v2, bundle2v2, bundle3v2);

        m_servlet = new TestDeploymentServlet("007");
        m_http.registerServlet("/deployment", m_servlet, null, null);
        m_http.registerServlet("/agent", new TestUpdateServlet(), null, null);
        m_http.registerServlet("/auditlog", new TestAuditlogServlet(), null, null);

    }

    public void tearDown() throws Exception {
        m_http.unregister("/deployment");
        m_http.unregister("/agent");
        m_http.unregister("/auditlog");
    }

    public void testDeployment() throws Exception {

        AgentControl control = getService(AgentControl.class);
        control.getConfigurationHandler().put(AgentConstants.CONFIG_LOGGING_LEVEL, LoggingHandler.Levels.DEBUG.name());
        control.getConfigurationHandler().put(AgentConstants.CONFIG_IDENTIFICATION_AGENTID, "007");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_STREAMING, "true");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_SYNCDELAY, "1");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_SYNCINTERVAL, "2");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_RETRIES, "2");
        waitForInstalledVersion(Version.emptyVersion);

        expectSuccessfulDeployment(m_package1, Failure.VERSIONS_RETRY_AFTER);
        expectSuccessfulDeployment(m_package2, Failure.DEPLOYMENT_RETRY_AFTER);
        expectSuccessfulDeployment(m_package3, Failure.EMPTY_STREAM);
        expectSuccessfulDeployment(m_package4, Failure.CORRUPT_STREAM);
        expectSuccessfulDeployment(m_package5, Failure.ABORT_STREAM);
        expectSuccessfulDeployment(m_package6, null);

        resetAgentBundleState();

        control = getService(AgentControl.class);
        control.getConfigurationHandler().put(AgentConstants.CONFIG_LOGGING_LEVEL, LoggingHandler.Levels.DEBUG.name());
        control.getConfigurationHandler().put(AgentConstants.CONFIG_IDENTIFICATION_AGENTID, "007");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_STREAMING, "true");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_SYNCDELAY, "2");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_SYNCINTERVAL, "2");
        control.getConfigurationHandler().put(AgentConstants.CONFIG_CONTROLLER_RETRIES, "2");
        m_servlet.clearPackages();

        control.getConfigurationHandler().put("ace.agent.controller.updateStreaming", "false");
        control.getConfigurationHandler().put("ace.agent.identification.agentId", "007");
        control.getConfigurationHandler().put("ace.agent.controller.syncDelay", "2");
        waitForInstalledVersion(Version.emptyVersion);

        expectSuccessfulDeployment(m_package1, Failure.VERSIONS_RETRY_AFTER);
        expectSuccessfulDeployment(m_package2, Failure.DEPLOYMENT_RETRY_AFTER);
        expectSuccessfulDeployment(m_package3, Failure.EMPTY_STREAM);
        expectSuccessfulDeployment(m_package4, Failure.CORRUPT_STREAM);
        expectSuccessfulDeployment(m_package5, Failure.ABORT_STREAM);
        expectSuccessfulDeployment(m_package6, null);
    }

    private void expectSuccessfulDeployment(TestPackage dpackage, Failure failure) throws Exception {
        synchronized (m_servlet) {
            if (failure != null) {
                m_servlet.setFailure(Failure.VERSIONS_RETRY_AFTER);
            }
            m_servlet.addPackage(dpackage);
            m_listener.getTopics().clear();
        }
        waitForEventReceived("org/osgi/service/deployment/INSTALL");
        waitForEventReceived("org/osgi/service/deployment/COMPLETE");
        waitForInstalledVersion(dpackage.getVersion());
    }

    private void waitForInstalledVersion(Version version) throws Exception {
        ServiceReference reference = m_bundleContext.getServiceReference(AgentControl.class.getName());
        AgentControl control = (AgentControl) m_bundleContext.getService(reference);
        int timeout = 100;
        while (!control.getDeploymentHandler().getInstalledVersion().equals(version)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                m_bundleContext.ungetService(reference);
                fail("Timed out while waiting for deployment " + version);
            }
        }
        m_bundleContext.ungetService(reference);
    }

    private void waitForEventReceived(String topic) throws Exception {
        int timeout = 100;
        while (!m_listener.getTopics().contains(topic)) {
            Thread.sleep(100);
            if (timeout-- <= 0) {
                fail("Timed out while waiting for event " + topic);
            }
        }
    }

    private static File createPackage(String name, Version version, File... bundles) throws Exception {
        DeploymentPackageBuilder builder = DeploymentPackageBuilder.createDeploymentPackage(name, version.toString());
        for (File bundle : bundles) {
            builder.addBundle(bundle.toURI().toURL());
        }
        File file = File.createTempFile("testpackage", ".jar");
        OutputStream fos = new FileOutputStream(file);
        builder.generate(fos);
        fos.close();
        return file;
    }

    private static File createBundle(String bsn, Version version, String... headers) throws Exception {
        Builder b = new Builder();
        b.setProperty("Bundle-SymbolicName", bsn);
        b.setProperty("Bundle-Version", version.toString());
        for (int i = 0; i < headers.length; i += 2) {
            b.setProperty(headers[i], headers[i + 1]);
        }
        b.setProperty("Include-Resource", "bnd.bnd"); // prevent empty jar bug
        Jar jar = b.build();
        jar.getManifest(); // Not sure whether this is needed...
        File file = File.createTempFile("testbundle", ".jar");
        jar.write(file);
        return file;
    }

    private static class TestBundle {

        private final String m_name;
        private final Version m_version;
        private final String[] m_headers;
        private final File m_file;

        public TestBundle(String name, Version version, String... headers) throws Exception {
            m_name = name;
            m_version = version;
            m_headers = headers;
            m_file = createBundle(name, version, headers);
        }

        public String getName() {
            return m_name;
        }

        public Version getVersion() {
            return m_version;
        }

        public String[] getHeaders() {
            return m_headers;
        }

        public File getFile() {
            return m_file;
        }
    }

    private static class TestPackage {

        private final String m_name;
        private final Version m_version;
        private final TestBundle[] m_bundles;
        private final File m_file;

        public TestPackage(String name, Version version, TestBundle... bundles) throws Exception {
            m_name = name;
            m_version = version;
            m_bundles = bundles;

            File[] files = new File[bundles.length];
            for (int i = 0; i < bundles.length; i++) {
                files[i] = bundles[i].getFile();
            }
            m_file = createPackage(name, version, files);
        }

        public String getName() {
            return m_name;
        }

        public Version getVersion() {
            return m_version;
        }

        public TestBundle[] getBundles() {
            return m_bundles;
        }

        public File getFile() {
            return m_file;
        }
    }

    private static class TestEventListener implements EventListener {

        private final List<String> m_topics = new ArrayList<String>();

        @Override
        public synchronized void handle(String topic, Map<String, String> payload) {
            System.out.println("Event: " + topic + " => " + payload);
            m_topics.add(topic);
        }

        public List<String> getTopics() {
            return m_topics;
        }
    }

    private static class TestDeploymentServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final Map<String, TestPackage> m_packages = new HashMap<String, TestPackage>();
        private final String m_agentId;

        private Failure m_failure;

        public TestDeploymentServlet(String agentId) {
            m_agentId = agentId;
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
                sendPackage(dpackage, resp);
            }
        }

        public synchronized void addPackage(TestPackage testPackage) {
            m_packages.put(testPackage.getVersion().toString(), testPackage);
        }

        public synchronized void clearPackages() {
            m_packages.clear();
        }

        public synchronized void setFailure(Failure failure) {
            m_failure = failure;
        }

        private void sendPackage(TestPackage dpackage, HttpServletResponse resp) throws IOException {
            if (m_failure == Failure.DEPLOYMENT_RETRY_AFTER) {
                resp.addHeader("Retry-After", "3");
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                m_failure = null;
                return;
            }

            long middle = dpackage.getFile().length() / 2;
            FileInputStream fis = null;
            OutputStream os = null;
            try {
                fis = new FileInputStream(dpackage.getFile());
                os = resp.getOutputStream();

                if (m_failure == Failure.EMPTY_STREAM) {
                    m_failure = null;
                    return;
                }

                if (m_failure == Failure.CORRUPT_STREAM) {
                    os.write("garbage".getBytes());
                    m_failure = null;
                }

                int b;
                int count = 0;
                while ((b = fis.read()) != -1) {
                    os.write(b);
                    if (count++ == middle && m_failure == Failure.ABORT_STREAM) {
                        m_failure = null;
                        break;
                    }
                }

            }
            finally {
                fis.close();
                if (os != null) {
                    os.close();
                }
            }
        }

        private void sendVersions(HttpServletResponse resp) throws IOException {
            if (m_failure == Failure.VERSIONS_RETRY_AFTER) {
                resp.addHeader("Retry-After", "3");
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
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

    private static class TestUpdateServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setStatus(200);
        }
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
}
