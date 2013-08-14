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

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Testing {@link ConnectionHandlerImpl}
 * 
 */
// TODO test CLIENT_CERT
public class ConnectionHandlerImplTest extends BaseAgentTest {

    static class BasicAuthServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final String m_authHeader;

        public BasicAuthServlet(String username, String password) {
            m_authHeader = "Basic " + new String(Base64.encodeBase64((username + ":" + password).getBytes()));
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.equals(m_authHeader))
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Requires Basic Auth");
            resp.setStatus(HttpServletResponse.SC_OK);
        }

    }

    Map<String, String> m_configuration = new HashMap<String, String>();
    ConnectionHandler m_connectionHandler;
    TestWebServer m_webServer;
    String m_user = "Mickey";
    String m_pass = "Mantle";
    URL m_basicAuthURL;

    @BeforeTest
    public void setUpAgain() throws Exception {

        int port = 8880;
        m_basicAuthURL = new URL("http://localhost:" + port + "/basicauth");
        m_webServer = new TestWebServer(port, "/", "generated");
        m_webServer.addServlet(new BasicAuthServlet(m_user, m_pass), "/basicauth/*");
        m_webServer.start();

        ConfigurationHandler configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(configurationHandler.getMap()).andReturn(m_configuration).anyTimes();

        AgentContext agentContext = addTestMock(AgentContext.class);
        expect(agentContext.getConfigurationHandler()).andReturn(configurationHandler).anyTimes();

        replayTestMocks();
        m_connectionHandler = new ConnectionHandlerImpl(agentContext);
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        m_webServer.stop();
        verifyTestMocks();
    }

    @Test
    public void testBasicAuthFORBIDDEN() throws Exception {
        m_configuration.clear();
        HttpURLConnection connection = (HttpURLConnection) m_connectionHandler.getConnection(m_basicAuthURL);
        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testBasicAuthOK() throws Exception {
        m_configuration.put(ConnectionHandlerImpl.PROP_AUTHTYPE, "BASIC");
        m_configuration.put(ConnectionHandlerImpl.PROP_AUTHUSER, m_user);
        m_configuration.put(ConnectionHandlerImpl.PROP_AUTHPASS, m_pass);
        HttpURLConnection connection = (HttpURLConnection) m_connectionHandler.getConnection(m_basicAuthURL);
        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_OK);
    }
}
