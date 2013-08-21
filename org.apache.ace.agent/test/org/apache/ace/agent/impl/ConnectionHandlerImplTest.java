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
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.notNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Testing {@link ConnectionHandlerImpl},
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

    private TestWebServer m_webServer;
    private String m_user = "Mickey";
    private String m_pass = "Mantle";
    private URL m_basicAuthURL;

    private AgentContext m_agentContext;
    private ConfigurationHandler m_configurationHandler;
    private ConnectionHandler m_connectionHandler;

    @BeforeTest
    public void setUpAgain() throws Exception {

        int port = 8880;
        m_basicAuthURL = new URL("http://localhost:" + port + "/basicauth");
        m_webServer = new TestWebServer(port, "/", "generated");
        m_webServer.addServlet(new BasicAuthServlet(m_user, m_pass), "/basicauth/*");
        m_webServer.start();

        m_configurationHandler = addTestMock(ConfigurationHandler.class);
        m_agentContext = addTestMock(AgentContext.class);
        expect(m_agentContext.getConfigurationHandler()).andReturn(m_configurationHandler).anyTimes();
        replayTestMocks();

        m_connectionHandler = new ConnectionHandlerImpl();
        startHandler(m_connectionHandler, m_agentContext);
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        stopHandler(m_connectionHandler);
        m_webServer.stop();
        verifyTestMocks();
    }

    @Test
    public void testBasicAuthFORBIDDEN() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(notNull(String.class), anyObject(String.class))).andReturn(null).anyTimes();
        replay(m_configurationHandler);
        HttpURLConnection connection = (HttpURLConnection) m_connectionHandler.getConnection(m_basicAuthURL);
        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_FORBIDDEN);

    }

    @Test
    public void testBasicAuthOK() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class))).andReturn("BASIC").anyTimes();
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHUSER), anyObject(String.class))).andReturn(m_user).anyTimes();
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHPASS), anyObject(String.class))).andReturn(m_pass).anyTimes();
        replay(m_configurationHandler);
        HttpURLConnection connection = (HttpURLConnection) m_connectionHandler.getConnection(m_basicAuthURL);
        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_OK);
    }
}
