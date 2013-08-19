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
import static org.testng.Assert.assertNull;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.impl.AgentContext;
import org.apache.ace.agent.impl.ConnectionHandlerImpl;
import org.apache.ace.agent.impl.DiscoveryHandlerImpl;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DiscoveryHandlerImplTest extends BaseAgentTest {

    Map<String, String> configuration = new HashMap<String, String>();

    DiscoveryHandler m_discoveryHandler;

    TestWebServer m_webServer;
    TestWebServer m_secondWebServer;

    URL m_availableURL;
    URL m_unavailableURL;

    @BeforeTest
    public void setUpAgain() throws Exception {

        int port = 8882;
        m_webServer = new TestWebServer(port, "/", "generated");
        m_webServer.start();

        m_availableURL = new URL("http://localhost:" + port);
        m_unavailableURL = new URL("http://localhost:9999");

        AgentContext agentContext = addTestMock(AgentContext.class);
        m_discoveryHandler = new DiscoveryHandlerImpl(agentContext);

        ConfigurationHandler configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(configurationHandler.getMap()).andReturn(configuration).anyTimes();

        ConnectionHandler connectionHandler = new ConnectionHandlerImpl(agentContext);

        expect(agentContext.getConfigurationHandler()).andReturn(configurationHandler).anyTimes();
        expect(agentContext.getConnectionHandler()).andReturn(connectionHandler).anyTimes();

        replayTestMocks();
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        m_webServer.stop();
        verifyTestMocks();
    }

    @Test
    public void testAvailableURL() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_availableURL.toExternalForm());
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testUnavailableURL_unavailable() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_unavailableURL.toExternalForm());
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testUnavailableAfterConfigUpdate() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_availableURL.toExternalForm());
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_unavailableURL.toExternalForm());
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testAvailableAfterConfigUpdate() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_unavailableURL.toExternalForm());
        assertNull(m_discoveryHandler.getServerUrl());
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_availableURL.toExternalForm());
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testAvailableAfterUnavailableURL() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, m_unavailableURL.toExternalForm() + "," + m_availableURL.toExternalForm());
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    // tmp default in implementation
    @Test(enabled=false)
    public void testNoURLConfig() throws Exception {
        configuration.clear();
        assertNull(m_discoveryHandler.getServerUrl());
    }

    // tmp default in implementation
    @Test(enabled=false)
    public void testEmptyURLConfig() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, "");
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testBadURLConfig() throws Exception {
        configuration.put(DiscoveryHandlerImpl.DISCOVERY_CONFIG_KEY, "fooBar");
        assertNull(m_discoveryHandler.getServerUrl());
    }
}
