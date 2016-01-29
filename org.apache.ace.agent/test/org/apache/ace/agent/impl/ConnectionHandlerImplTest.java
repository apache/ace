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
import javax.xml.bind.DatatypeConverter;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.ConnectionHandler.Types;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.osgi.framework.BundleContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Testing {@link ConnectionHandlerImpl},
 */
public class ConnectionHandlerImplTest extends BaseAgentTest {

    static class BasicAuthServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final String m_authHeader;

        public BasicAuthServlet(String username, String password) {
            m_authHeader = "Basic ".concat(DatatypeConverter.printBase64Binary((username + ":" + password).getBytes()));
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.equals(m_authHeader)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Requires Basic Auth");
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
            }
        }
    }

    private static final int PORT = 8880;
    private static final String USERNAME = "john.doe";
    private static final String PASSWORD = "secret";

    private TestWebServer m_webServer;
    private URL m_basicAuthURL;
    private AgentContextImpl m_agentContext;

    @BeforeClass
    public void setUpOnceAgain() throws Exception {
        m_basicAuthURL = new URL("http://localhost:" + PORT + "/basicauth");

        m_webServer = new TestWebServer(PORT, "/", "generated");
        m_webServer.addServlet(new BasicAuthServlet(USERNAME, PASSWORD), "/basicauth/*");
        m_webServer.start();

        BundleContext bc = mockBundleContext();
        
        m_agentContext = mockAgentContext();
        m_agentContext.setHandler(EventsHandler.class, new EventsHandlerImpl(bc));
        m_agentContext.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl(bc));
        m_agentContext.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());

        replayTestMocks();
        m_agentContext.start();
    }

    @AfterClass
    public void tearDownOnceAgain() throws Exception {
        m_agentContext.stop();
        m_webServer.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testBasicAuthFORBIDDEN() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(AgentConstants.CONFIG_CONNECTION_AUTHTYPE, Types.NONE.name());

        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        configurationHandler.putAll(props);

        ConnectionHandler connectionHandler = m_agentContext.getHandler(ConnectionHandler.class);
        HttpURLConnection connection = (HttpURLConnection) connectionHandler.getConnection(m_basicAuthURL);

        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testBasicAuthOK() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(AgentConstants.CONFIG_CONNECTION_AUTHTYPE, Types.BASIC.name());
        props.put(AgentConstants.CONFIG_CONNECTION_USERNAME, USERNAME);
        props.put(AgentConstants.CONFIG_CONNECTION_PASSWORD, PASSWORD);

        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        configurationHandler.putAll(props);

        ConnectionHandler connectionHandler = m_agentContext.getHandler(ConnectionHandler.class);

        HttpURLConnection connection = (HttpURLConnection) connectionHandler.getConnection(m_basicAuthURL);
        assertEquals(connection.getResponseCode(), HttpServletResponse.SC_OK);
    }
}
