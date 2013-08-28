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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.net.URL;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Testing {@link DiscoveryHandlerImpl}.
 */
public class DiscoveryHandlerImplTest extends BaseAgentTest {

    private final int PORT = 8882;
    private TestWebServer m_webServer;
    private URL m_availableURL;
    private URL m_unavailableURL;

    private AgentContext m_agentContext;
    private AgentContextImpl m_agentContextImpl;

    @BeforeTest
    public void setUpOnceAgain() throws Exception {
        m_webServer = new TestWebServer(PORT, "/", "generated");
        m_webServer.start();
        m_availableURL = new URL("http://localhost:" + PORT);
        m_unavailableURL = new URL("http://localhost:9999");

        m_agentContextImpl = mockAgentContext();
        m_agentContext = m_agentContextImpl;
        m_agentContextImpl.setHandler(DiscoveryHandler.class, new DiscoveryHandlerImpl());
        m_agentContextImpl.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        replayTestMocks();
        m_agentContextImpl.start();
    }

    @AfterTest
    public void tearDownOnceAgain() throws Exception {
        m_webServer.stop();

        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testAvailableURL() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).anyTimes();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testUnavailableURL_unavailable() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).anyTimes();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());
    }

    @Test
    public void testUnavailableAfterConfigUpdate() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).once();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).once();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL);
        assertNull(discoveryHandler.getServerUrl());
    }

    @Test
    public void testAvailableAfterConfigUpdate() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).once();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).once();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testAvailableAfterUnavailableURL() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm() + "," + m_availableURL.toExternalForm()).once();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testEmptyURLConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn("").once();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());
    }

    @Test
    public void testBadURLConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_CONNECTION_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_DISCOVERY_SERVERURLS), anyObject(String.class)))
            .andReturn("foobar").once();
        expect(configurationHandler.getBoolean(AgentConstants.CONFIG_DISCOVERY_CHECKING, false))
            .andReturn(true).anyTimes();
        replay(configurationHandler);
        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());
    }
}
