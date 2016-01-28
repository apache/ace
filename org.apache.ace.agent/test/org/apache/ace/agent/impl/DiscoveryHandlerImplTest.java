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
import static org.testng.Assert.assertNull;

import java.net.URL;
import java.util.List;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.osgi.framework.BundleContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Testing {@link DiscoveryHandlerImpl}.
 */
public class DiscoveryHandlerImplTest extends BaseAgentTest {

    private static final int PORT = 8882;

    private TestWebServer m_webServer;
    private URL m_availableURL1;
    private URL m_availableURL2;
    private URL m_unavailableURL1;
    private URL m_unavailableURL2;

    private AgentContext m_agentContext;
    private AgentContextImpl m_agentContextImpl;

    @BeforeClass
    public void setUpOnceAgain() throws Exception {
        m_webServer = new TestWebServer(PORT, "/", "generated");
        m_webServer.start();
        m_availableURL1 = new URL("http://localhost:" + PORT);
        m_availableURL2 = new URL("http://127.0.0.1:" + PORT);
        m_unavailableURL1 = new URL("http://localhost:19999");
        m_unavailableURL2 = new URL("http://127.0.0.1:19999");

        BundleContext bc = mockBundleContext();

        m_agentContextImpl = mockAgentContext();
        m_agentContext = m_agentContextImpl;
        // Make sure the default server URL is not reachable, as used for this test...
        m_agentContextImpl.setHandler(DiscoveryHandler.class, new DiscoveryHandlerImpl(m_unavailableURL1.toExternalForm(), true));
        m_agentContextImpl.setHandler(EventsHandler.class, new EventsHandlerImpl(bc));
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl(bc));
        m_agentContextImpl.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        replayTestMocks();
        m_agentContextImpl.start();
    }

    @AfterClass
    public void tearDownOnceAgain() throws Exception {
        m_webServer.stop();

        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    private String concat(URL... urls) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(urls[i].toExternalForm());
        }
        return sb.toString();
    }
    
    @Test
    public void testAvailableURL() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_availableURL1, m_availableURL2), 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL1);
    }

    @Test
    public void testUnavailableURL_unavailable() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_unavailableURL1, m_unavailableURL2), 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());
    }

    @Test
    public void testUnavailableAfterConfigUpdate() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_availableURL1, m_availableURL2), 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL1);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_unavailableURL1, m_unavailableURL2));

        assertNull(discoveryHandler.getServerUrl());
    }

    @Test
    public void testAvailableAfterConfigUpdate() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_unavailableURL1, m_unavailableURL2), 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl());

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_availableURL1));

        assertEquals(discoveryHandler.getServerUrl(), m_availableURL1);
    }

    @Test
    public void testAvailableAfterUnavailableURL() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, concat(m_unavailableURL1, m_availableURL1), 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_availableURL1);
    }

    @Test
    public void testEmptyURLConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, "", 
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertEquals(discoveryHandler.getServerUrl(), m_unavailableURL1);
    }

    @Test
    public void testBadURLConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, 
            AgentConstants.CONFIG_DISCOVERY_SERVERURLS, "invalidURL",
            AgentConstants.CONFIG_DISCOVERY_CHECKING, "true");

        DiscoveryHandler discoveryHandler = m_agentContext.getHandler(DiscoveryHandler.class);
        assertNull(discoveryHandler.getServerUrl()); // we cannot resolve this during the configuration change...
    }
    
    @Test
    public void testSplitAndMergeUrls() throws Exception {
        DiscoveryHandlerImpl discoveryHandler = (DiscoveryHandlerImpl) m_agentContext.getHandler(DiscoveryHandler.class);
        String urlsValue = "http://a/,http://b/,http://c/";
		List<String> urls = discoveryHandler.splitUrls(urlsValue);
        assertEquals(urls.size(), 3);
        assertEquals(urls.get(0), "http://a/");
        assertEquals(urls.get(1), "http://b/");
        assertEquals(urls.get(2), "http://c/");
        String mergedUrls = discoveryHandler.mergeUrls(urls);
        assertEquals(mergedUrls, urlsValue);
    }
}
