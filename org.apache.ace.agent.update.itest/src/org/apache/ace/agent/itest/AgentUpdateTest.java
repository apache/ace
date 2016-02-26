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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
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
public class AgentUpdateTest extends IntegrationTestBase {
    final class DummyAgentVersionServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            final AgentUpdateOBRServlet servlet = AgentUpdateTest.this.m_servlet;
            final String path = "/defaultTargetID/org.apache.ace.agent/versions/";

            String pathInfo = req.getPathInfo();
            if (path.equals(pathInfo)) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(servlet.m_currentVersion);
                resp.getWriter().println(servlet.m_nextVersion);
            }
            else if (pathInfo.startsWith(path)) {
                String version = pathInfo.substring(path.length());
                resp.sendRedirect("/obr/" + version + ".jar");
            }
            else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    private static class DummyAuditLogServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String pathInfo = req.getPathInfo();
            if ("/send".equals(pathInfo)) {
                resp.setStatus(HttpServletResponse.SC_OK);
            }
            else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    private static class DeploymentServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    static class AgentUpdateOBRServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        final String m_currentVersion;
        final String m_nextVersion;

        volatile Phase m_phase;
        volatile CountDownLatch m_latch;

        public AgentUpdateOBRServlet(Version currentVersion) {
            m_currentVersion = currentVersion.toString();
            // Determine the next version we want to update to...
            m_nextVersion = new Version(currentVersion.getMajor(), currentVersion.getMinor(), currentVersion.getMicro() + 1).toString();
        }

        public synchronized CountDownLatch setPhase(Phase phase, CountDownLatch latch) {
            m_phase = phase;
            m_latch = latch;
            System.out.printf("Updating in phase: %s (from v%s to v%s)...%n", phase, m_currentVersion, m_nextVersion);
            return latch;
        }

        public Version getNextAgentVersion() {
            return new Version(m_nextVersion);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String path = req.getPathInfo();
            if (path == null) {
                path = "/";
            }
            
            String currentAgentJAR = m_currentVersion + ".jar";
            String nextAgentJAR = m_nextVersion + ".jar";

            if (path.endsWith(currentAgentJAR)) {
                write(getBundle(), m_currentVersion, resp.getOutputStream());
            }
            else if (path.endsWith(nextAgentJAR)) {
                write(getBundle(), m_nextVersion, resp.getOutputStream());
            }
            else {
                throw new Error("Statement should never be reached.");
            }
        
        }

        protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("text/plain");
            response.flushBuffer();
        }

        private InputStream getBundle() throws IOException {
            return new FileInputStream(new File("../org.apache.ace.agent/generated/org.apache.ace.agent.jar"));
        }

        private synchronized void write(InputStream object, String version, OutputStream outputStream) throws IOException {
            JarInputStream jis = new JarInputStream(object);
            Manifest manifest = jis.getManifest();
            manifest.getMainAttributes().put(new Attributes.Name("Bundle-Version"), version);
            if (m_phase == Phase.BUNDLE_DOES_NOT_START && m_nextVersion.equals(version)) {
                manifest.getMainAttributes().put(new Attributes.Name("Bundle-Activator"), "org.foo.NonExistingClass");
            }
            if (m_phase == Phase.BUNDLE_DOES_NOT_RESOLVE && m_nextVersion.equals(version)) {
                manifest.getMainAttributes().put(new Attributes.Name("Import-Package"), "org.foo.nonexistingpackage");
            }
            JarOutputStream jos = new JarOutputStream(outputStream, manifest);
            JarEntry entry;
            int length;
            byte[] buffer = new byte[4096];
            while ((entry = jis.getNextJarEntry()) != null) {
                jos.putNextEntry(entry);
                while ((length = jis.read(buffer)) != -1) {
                    jos.write(buffer, 0, length);
                    if (m_phase == Phase.CORRUPT_STREAM && m_nextVersion.equals(version)) {
                        jos.write("garbage".getBytes());
                    }
                }
                jos.closeEntry();
                jis.closeEntry();
            }
            jis.close();
            jos.close();
            if (m_phase == Phase.BUNDLE_WORKS && m_nextVersion.equals(version)) {
                m_latch.countDown();
            }
            if (m_phase != Phase.BUNDLE_WORKS && m_currentVersion.equals(version)) {
                m_latch.countDown();
            }
        }
    }

    private enum Phase {
        CORRUPT_STREAM, BUNDLE_DOES_NOT_RESOLVE, BUNDLE_DOES_NOT_START, BUNDLE_WORKS
    }

    private volatile HttpService m_http;
    private volatile AgentUpdateOBRServlet m_servlet;

    public void testAgentUpdate() throws Exception {
        final int defaultTimeout = 150;

        CountDownLatch latch;

        latch = m_servlet.setPhase(Phase.CORRUPT_STREAM, new CountDownLatch(1));
        assertTrue("Timed out while recovering from update with broken stream.", latch.await(defaultTimeout, TimeUnit.SECONDS));

        latch = m_servlet.setPhase(Phase.BUNDLE_DOES_NOT_RESOLVE, new CountDownLatch(1));
        assertTrue("Timed out while recovering from update with agent that does not resolve.", latch.await(defaultTimeout, TimeUnit.SECONDS));

        latch = m_servlet.setPhase(Phase.BUNDLE_DOES_NOT_START, new CountDownLatch(1));
        assertTrue("Timed out while recovering from update with agent that does not start.", latch.await(defaultTimeout, TimeUnit.SECONDS));

        latch = m_servlet.setPhase(Phase.BUNDLE_WORKS, new CountDownLatch(1));
        assertTrue("Timed out while starting working bundle?!", latch.await(defaultTimeout, TimeUnit.SECONDS));

        int timeout = defaultTimeout;
        while (timeout-- > 0) {
            Version agentVersion = getCurrentAgentVersion();
            if (agentVersion.equals(m_servlet.getNextAgentVersion())) {
                return;
            }

            Thread.sleep(100);
        }
        fail("Timed out waiting for update with new agent.");
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        String serverURL = String.format("http://localhost:%d/", TestConstants.PORT);

        Map<String, String> props = new HashMap<>();
        props.put(AgentConstants.CONFIG_DISCOVERY_SERVERURLS, serverURL);

        AgentControl agentControl = getService(AgentControl.class);
        agentControl.getConfigurationHandler().putAll(props);
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        // We need to know the *current* version of the agent, as we're trying to get it updated to a later version!
        Version currentAgentVersion = getCurrentAgentVersion();

        m_servlet = new AgentUpdateOBRServlet(currentAgentVersion);

        String url = String.format("http://localhost:%d/obr", TestConstants.PORT);
        NetUtils.waitForURL_NotFound(url);

        m_http.registerServlet("/obr", m_servlet, null, null);
        m_http.registerServlet("/auditlog", new DummyAuditLogServlet(), null, null);
        m_http.registerServlet("/deployment", new DeploymentServlet(), null, null);
        m_http.registerServlet("/agent", new DummyAgentVersionServlet(), null, null);

        NetUtils.waitForURL(url);
    }

    @Override
    protected void doTearDown() throws Exception {
        m_http.unregister("/obr");
        m_http.unregister("/auditlog");
        m_http.unregister("/deployment");
        m_http.unregister("/agent");
    }

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
        };
    }

    private Version getCurrentAgentVersion() {
        Bundle agent = null;
        for (Bundle bundle : m_bundleContext.getBundles()) {
            if ("org.apache.ace.agent".equals(bundle.getSymbolicName())) {
                agent = bundle;
                break;
            }
        }
        assertNotNull("Agent bundle not found?!", agent);
        return agent.getVersion();
    }
}
