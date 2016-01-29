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
package org.apache.ace.agent.impl;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.notNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Testing {@link DeploymentHandlerImpl}.
 */
public class DeploymentHandlerImplTest extends BaseAgentTest {

    static class TestDeploymentServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private Map<String, File> m_packages = new HashMap<>();

        @SuppressWarnings("deprecation")
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith("/"))
                pathInfo = pathInfo.substring(1);

            if (pathInfo != null && m_packages.containsKey(pathInfo)) {
                File file = m_packages.get(pathInfo);
                resp.setHeader(AgentConstants.HEADER_DPSIZE, "" + file.length());
                FileInputStream fis = new FileInputStream(file);
                OutputStream os = resp.getOutputStream();
                int b;
                while ((b = fis.read()) != -1) {
                    os.write(b);
                }
                fis.close();
                os.close();
            }
            else {
                PrintWriter writer = resp.getWriter();
                for (String version : m_packages.keySet()) {
                    writer.println(version);
                }
                writer.close();
            }
            resp.setStatus(200, "voila");
        }

        void addPackage(String version, File file) {
            m_packages.put(version, file);
        }
    }

    int port = 8881;
    String identification = "agent";
    URL serverURL = null;

    private TestWebServer m_webserver;
    private File m_200file;
    private Version m_version1 = Version.parseVersion("1.0.0");
    private Version m_version2 = Version.parseVersion("2.0.0");
    private Version m_version3 = Version.parseVersion("3.0.0");
    long m_remotePackageSize = 0l;

    private AgentContextImpl m_agentContext;

    @BeforeClass
    public void setUpOnceAgain() throws Exception {
        serverURL = new URL("http://localhost:" + port + "/");
        m_webserver = new TestWebServer(port, "/", "generated");
        m_webserver.start();

        m_200file = new File(new File("generated"), "testfile.txt");
        DigestOutputStream dos = new DigestOutputStream(new FileOutputStream(m_200file), MessageDigest.getInstance("MD5"));
        for (int i = 0; i < 10000; i++) {
            dos.write(String.valueOf(System.currentTimeMillis()).getBytes());
            dos.write(" Lorum Ipsum Lorum Ipsum Lorum Ipsum Lorum Ipsum Lorum Ipsum\n".getBytes());
        }
        dos.close();

        TestDeploymentServlet servlet = new TestDeploymentServlet();
        servlet.addPackage(m_version1.toString(), m_200file);
        servlet.addPackage(m_version2.toString(), m_200file);
        servlet.addPackage(m_version3.toString(), m_200file);

        m_remotePackageSize = m_200file.length();
        m_webserver.addServlet(servlet, "/deployment/" + identification + "/versions/*");

        DeploymentPackage deploymentPackage1 = addTestMock(DeploymentPackage.class);
        expect(deploymentPackage1.getName()).andReturn(identification).anyTimes();
        expect(deploymentPackage1.getVersion()).andReturn(Version.parseVersion("1.0.0")).anyTimes();

        DeploymentPackage deploymentPackage2 = addTestMock(DeploymentPackage.class);
        expect(deploymentPackage2.getName()).andReturn(identification).anyTimes();
        expect(deploymentPackage2.getVersion()).andReturn(Version.parseVersion("2.0.0")).anyTimes();

        DeploymentPackage deploymentPackage3 = addTestMock(DeploymentPackage.class);
        expect(deploymentPackage3.getName()).andReturn(identification).anyTimes();
        expect(deploymentPackage3.getVersion()).andReturn(Version.parseVersion("3.0.0")).anyTimes();

        IdentificationHandler identificationHandler = addTestMock(IdentificationHandler.class);
        expect(identificationHandler.getAgentId()).andReturn(identification).anyTimes();

        DiscoveryHandler discoveryHandler = addTestMock(DiscoveryHandler.class);
        expect(discoveryHandler.getServerUrl()).andReturn(serverURL).anyTimes();

        ConfigurationHandler configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(configurationHandler.get(notNull(String.class), anyObject(String.class))).andReturn(null).anyTimes();

        DeploymentAdmin deploymentAdmin = addTestMock(DeploymentAdmin.class);
        expect(deploymentAdmin.listDeploymentPackages()).andReturn(
            new DeploymentPackage[] { deploymentPackage2, deploymentPackage1 }).anyTimes();
        expect(deploymentAdmin.installDeploymentPackage(notNull(InputStream.class)
            )).andReturn(deploymentPackage3).once();

        m_agentContext = mockAgentContext();
        m_agentContext.setHandler(IdentificationHandler.class, identificationHandler);
        m_agentContext.setHandler(DiscoveryHandler.class, discoveryHandler);
        m_agentContext.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContext.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        m_agentContext.setHandler(DeploymentHandler.class, new DeploymentHandlerImpl(deploymentAdmin));
        replayTestMocks();
        m_agentContext.start();
    }

    @AfterClass
    public void tearDownOnceAgain() throws Exception {
        m_webserver.stop();
        m_agentContext.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testCurrentVersion() throws Exception {
        DeploymentHandler deploymentHandler = m_agentContext.getHandler(DeploymentHandler.class);
        Version current = deploymentHandler.getInstalledVersion();
        assertNotNull(current);
        assertEquals(current, m_version2);
    }

    @Test
    public void testAvailableVersions() throws Exception {
        DeploymentHandler deploymentHandler = m_agentContext.getHandler(DeploymentHandler.class);
        SortedSet<Version> expected = new TreeSet<>();
        expected.add(m_version1);
        expected.add(m_version2);
        expected.add(m_version3);
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        assertNotNull(available);
        assertFalse(available.isEmpty());
        assertEquals(available, expected);
    }

    @Test
    public void testPackageSize() throws Exception {
        DeploymentHandler deploymentHandler = m_agentContext.getHandler(DeploymentHandler.class);
        long packageSize = deploymentHandler.getSize(m_version1, true);
        assertEquals(packageSize, m_remotePackageSize);
    }

    @Test
    public void testDeployPackage() throws Exception {
        DeploymentHandler deploymentHandler = m_agentContext.getHandler(DeploymentHandler.class);
        InputStream inputStream = deploymentHandler.getInputStream(m_version3, true);
        try {
            deploymentHandler.install(inputStream);
        }
        finally {
            inputStream.close();
        }
    }
}
