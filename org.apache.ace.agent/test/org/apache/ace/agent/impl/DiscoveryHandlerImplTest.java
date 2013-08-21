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
import static org.easymock.EasyMock.resetToNice;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.net.URL;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.osgi.service.log.LogService;
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
    private DiscoveryHandler m_discoveryHandler;
    private ConfigurationHandler m_configurationHandler;
    private ConnectionHandler m_connectionHandler;

    @BeforeTest
    public void setUpAgain() throws Exception {

        m_webServer = new TestWebServer(PORT, "/", "generated");
        m_webServer.start();
        m_availableURL = new URL("http://localhost:" + PORT);
        m_unavailableURL = new URL("http://localhost:9999");

        AgentContext agentContext = addTestMock(AgentContext.class);

        LogService logService = addTestMock(LogService.class);
        resetToNice(logService);

        m_discoveryHandler = new DiscoveryHandlerImpl();
        m_connectionHandler = new ConnectionHandlerImpl();
        m_configurationHandler = addTestMock(ConfigurationHandler.class);

        expect(agentContext.getConfigurationHandler()).andReturn(m_configurationHandler).anyTimes();
        expect(agentContext.getConnectionHandler()).andReturn(m_connectionHandler).anyTimes();

        replayTestMocks();
        startHandler(m_connectionHandler, agentContext);
        startHandler(m_discoveryHandler, agentContext);
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        stopHandler(m_connectionHandler);
        stopHandler(m_discoveryHandler);
        verifyTestMocks();
        m_webServer.stop();
    }

    @Test
    public void testAvailableURL() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).anyTimes();
        replay(m_configurationHandler);
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testUnavailableURL_unavailable() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).anyTimes();
        replay(m_configurationHandler);
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testUnavailableAfterConfigUpdate() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).once();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).once();
        replay(m_configurationHandler);
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testAvailableAfterConfigUpdate() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm()).once();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_availableURL.toExternalForm()).once();
        replay(m_configurationHandler);
        assertNull(m_discoveryHandler.getServerUrl());
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testAvailableAfterUnavailableURL() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn(m_unavailableURL.toExternalForm() + "," + m_availableURL.toExternalForm()).once();
        replay(m_configurationHandler);
        assertEquals(m_discoveryHandler.getServerUrl(), m_availableURL);
    }

    @Test
    public void testEmptyURLConfig() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn("").once();
        replay(m_configurationHandler);
        assertNull(m_discoveryHandler.getServerUrl());
    }

    @Test
    public void testBadURLConfig() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(ConnectionHandlerImpl.PROP_AUTHTYPE), anyObject(String.class)))
            .andReturn(null).anyTimes();
        expect(m_configurationHandler.get(eq(DiscoveryHandlerImpl.CONFIG_KEY_SERVERURLS), anyObject(String.class)))
            .andReturn("foobar").once();
        replay(m_configurationHandler);
        assertNull(m_discoveryHandler.getServerUrl());
    }
}
