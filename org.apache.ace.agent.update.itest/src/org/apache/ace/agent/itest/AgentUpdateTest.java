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
import java.io.PrintWriter;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.it.IntegrationTestBase;
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

    private volatile HttpService m_http;
    private volatile AgentUpdateOBRServlet m_servlet;

    private enum Phase {
        CORRUPT_STREAM, BUNDLE_DOES_NOT_RESOLVE, BUNDLE_DOES_NOT_START, BUNDLE_WORKS
    }

    private enum PhaseStatus {
        ACTIVE, DONE
    }

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
        };
    }

    @Override
    public void configureAdditionalServices() throws Exception {
        Thread.sleep(200);
        m_servlet = new AgentUpdateOBRServlet();
        m_http.registerServlet("/obr", m_servlet, null, null);
    }

    public void tearDown() throws Exception {
        m_http.unregister("/obr");
    }

    public void testAgentUpdate() throws Exception {

        int timeout = 50;
        m_servlet.setPhase(Phase.CORRUPT_STREAM);
        while (m_servlet.getPhaseStatus() == PhaseStatus.ACTIVE) {
            Thread.sleep(200);
            if (timeout-- <= 0) {
                fail("Timed out while recovering from update with broken stream.");
            }
        }
        timeout = 50;
        m_servlet.setPhase(Phase.BUNDLE_DOES_NOT_RESOLVE);
        while (m_servlet.getPhaseStatus() == PhaseStatus.ACTIVE) {
            Thread.sleep(200);
            if (timeout-- <= 0) {
                fail("Timed out while recovering from update with agent that does not resolve.");
            }
        }
        timeout = 50;
        m_servlet.setPhase(Phase.BUNDLE_DOES_NOT_START);
        while (m_servlet.getPhaseStatus() == PhaseStatus.ACTIVE) {
            Thread.sleep(200);
            if (timeout-- <= 0) {
                fail("Timed out while recovering from update with agent that does not start.");
            }
        }
        timeout = 50;
        m_servlet.setPhase(Phase.BUNDLE_WORKS);
        while (timeout-- > 0) {
            Thread.sleep(200);
            for (Bundle b : m_bundleContext.getBundles()) {
                if ("org.apache.ace.agent".equals(b.getSymbolicName())) {
                    if (b.getVersion().equals(new Version("2.0.0"))) {
                        return;
                    }
                }
            }
        }
        fail("Timed out waiting for update with new agent.");
    }

    private static class AgentUpdateOBRServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private Phase m_phase;
        private PhaseStatus m_phaseStatus;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String path = req.getPathInfo();
            if ("/repository.xml".equals(path)) {
                PrintWriter w = resp.getWriter();
                w.println("<?xml version='1.0' encoding='utf-8'?><repository>");
                w.println(createResource("org.apache.ace.agent", "1.0.0"));
                w.println(createResource("org.apache.ace.agent", "2.0.0"));
                w.println("</repository>");
            }
            else {
                if (path.endsWith("1.0.0.jar")) {
                    write(getBundle(), "1.0.0", resp.getOutputStream());
                }
                else if (path.endsWith("2.0.0.jar")) {
                    write(getBundle(), "2.0.0", resp.getOutputStream());
                }
                else {
                    throw new Error("Statement should never be reached.");
                }
            }
        }

        public synchronized void setPhase(Phase phase) {
            m_phase = phase;
            m_phaseStatus = PhaseStatus.ACTIVE;
        }

        public synchronized PhaseStatus getPhaseStatus() {
            return m_phaseStatus;
        }

        private InputStream getBundle() throws IOException {
            return new FileInputStream(new File("../org.apache.ace.agent/generated/org.apache.ace.agent.jar"));
        }

        private synchronized void write(InputStream object, String version, OutputStream outputStream) throws IOException {
            JarInputStream jis = new JarInputStream(object);
            Manifest manifest = jis.getManifest();
            manifest.getMainAttributes().put(new Attributes.Name("Bundle-Version"), version);
            if (m_phase == Phase.BUNDLE_DOES_NOT_START && "2.0.0".equals(version)) {
                manifest.getMainAttributes().put(new Attributes.Name("Bundle-Activator"), "org.foo.NonExistingClass");
            }
            if (m_phase == Phase.BUNDLE_DOES_NOT_RESOLVE && "2.0.0".equals(version)) {
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
                    if (m_phase == Phase.CORRUPT_STREAM && "2.0.0".equals(version)) {
                        jos.write("garbage".getBytes());
                    }
                }
                jos.closeEntry();
                jis.closeEntry();
            }
            jis.close();
            jos.close();
            if (m_phase == Phase.BUNDLE_WORKS && "2.0.0".equals(version)) {
                m_phaseStatus = PhaseStatus.DONE;
            }
            if (m_phase != Phase.BUNDLE_WORKS && "1.0.0".equals(version)) {
                m_phaseStatus = PhaseStatus.DONE;
            }
        }
    }

    private static String createResource(String bsn, String version) {
        return "<resource id='" + bsn + "/" + version + "' symbolicname='" + bsn + "' version='" + version + "' uri='" + bsn + "-" + version + ".jar'></resource>";
    }
}
