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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Integration test for Agent life-cycle. When the agent is configured to use external handler services, the agent must
 * only be active and available if and when those external services are available. In addition, when multiple services
 * for a handler are published the highest ranked should be used.
 */
public class AgentExtensionTest extends BaseAgentTest {

    public void testLifecycle() throws Exception {
        AgentControl agentControl = getService(AgentControl.class);
        assertNotNull(agentControl);

        getAgentBundle().stop();
        System.setProperty(AgentConstants.CONFIG_IDENTIFICATION_DISABLED, "true");
        System.setProperty(AgentConstants.CONFIG_DISCOVERY_DISABLED, "true");
        System.setProperty(AgentConstants.CONFIG_CONNECTION_DISABLED, "true");
        getAgentBundle().start();

        assertNull(locateService(AgentControl.class));

        ServiceRegistration<?> idreg1 = registerIdentification("TEST1", 1);
        assertNull(locateService(AgentControl.class));
        ServiceRegistration<?> direg1 = registerDiscovery(new URL("http://test1"), 1);
        assertNull(locateService(AgentControl.class));
        ServiceRegistration<?> coreg1 = registerConnectionHandler();
        assertNotNull(locateService(AgentControl.class));

        assertEquals("TEST1", locateService(AgentControl.class).getAgentId());

        ServiceRegistration<?> idreg2 = registerIdentification("TEST2", 2);

        assertEquals("TEST2", locateService(AgentControl.class).getAgentId());

        idreg2.unregister();

        assertEquals("TEST1", locateService(AgentControl.class).getAgentId());

        idreg1.unregister();

        assertNull(locateService(AgentControl.class));

        direg1.unregister();
        coreg1.unregister();
    }

    @Override
    protected void doTearDown() throws Exception {
        resetAgentBundleState();
    }

    private ServiceRegistration<IdentificationHandler> registerIdentification(final String id, final int rank) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, rank);
        return m_bundleContext.registerService(IdentificationHandler.class, new IdentificationHandler() {
            @Override
            public String getAgentId() {
                return id;
            }

            @Override
            public String toString() {
                return id;
            }
        }, props);
    }

    private ServiceRegistration<DiscoveryHandler> registerDiscovery(final URL url, final int rank) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, rank);
        return m_bundleContext
            .registerService(DiscoveryHandler.class, new DiscoveryHandler() {

                @Override
                public URL getServerUrl() {
                    return url;
                }
            }, props);
    }

    private ServiceRegistration<ConnectionHandler> registerConnectionHandler() {
        return m_bundleContext
            .registerService(ConnectionHandler.class, new ConnectionHandler() {
                @Override
                public URLConnection getConnection(URL url) throws IOException {
                    return url.openConnection();
                }
            }, null);
    }

    private <T> T locateService(Class<T> iface) {
        ServiceReference<T> reference = m_bundleContext.getServiceReference(iface);
        if (reference == null) {
            return null;
        }
        return m_bundleContext.getService(reference);
    }
}
