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

import java.lang.reflect.Method;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.osgi.framework.BundleContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link ConfigurationHandlerImpl}.
 */
public class ConfigurationHandlerImplTest extends BaseAgentTest {
    private BundleContext m_context;
    private AgentContextImpl m_agentContextImpl;

    @BeforeMethod(alwaysRun = true)
    public void setUpAgain(Method method) throws Exception {
        m_context = mockBundleContext();
        m_agentContextImpl = mockAgentContext(method.getName());
        replayTestMocks();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownAgain(Method method) throws Exception {
        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testConfigBooleanProps() throws Exception {
        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl(m_context);

        resetConfigurationHandler(configurationHandler);

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, "boolean1", "true", "boolean2", "false");

        assertEquals(configurationHandler.getBoolean("boolean1", false), true);
        assertEquals(configurationHandler.getBoolean("boolean2", true), false);

        assertEquals(configurationHandler.getBoolean("booleanX", true), true);
        assertEquals(configurationHandler.getBoolean("booleanY", false), false);
    }

    @Test
    public void testConfigClean() throws Exception {
        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl(m_context);

        resetConfigurationHandler(configurationHandler);

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get("key1", "default1"), "default1");

        // should be persisted
        configurationHandler = new ConfigurationHandlerImpl(m_context);

        resetConfigurationHandler(configurationHandler);

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get("key1", "default1"), "default1");
    }

    @Test
    public void testConfigLongProps() throws Exception {
        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl(m_context);

        resetConfigurationHandler(configurationHandler);

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, "long1", "42", "long2", "4");

        assertEquals(configurationHandler.getLong("long1", 0l), 42);
        assertEquals(configurationHandler.getLong("long2", 0l), 4l);

        assertEquals(configurationHandler.getLong("longX", 42l), 42l);
    }

    @Test
    public void testConfigSystemProps() throws Exception {
        String systemKey1 = AgentConstants.CONFIG_KEY_NAMESPACE + "key1";
        String systemKey2 = AgentConstants.CONFIG_KEY_NAMESPACE + "key2";

        System.setProperty(systemKey1, "value1");
        System.setProperty(systemKey2, "value2");

        resetConfigurationHandler();

        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get(systemKey1, "default1"), "value1");
        assertEquals(configurationHandler.get(systemKey2, "default2"), "value2");

        // System props should *not* be persisted, they are not in our control...

        System.clearProperty(systemKey1);
        System.clearProperty(systemKey2);

        resetConfigurationHandler();

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get(systemKey1, "qux"), "qux");
        assertEquals(configurationHandler.get(systemKey2, "quu"), "quu");

        // System props should not override the configured values...

        System.setProperty(systemKey1, "value1");
        System.setProperty(systemKey2, "value2");

        configureAgent(configurationHandler, systemKey1, "newvalue1", systemKey2, "newvalue2");

        resetConfigurationHandler();

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get(systemKey1, "qux"), "newvalue1");
        assertEquals(configurationHandler.get(systemKey2, "quu"), "newvalue2");

        // System props should not override if explicitly configured values are present...

        System.setProperty(systemKey1, "valueX");
        System.setProperty(systemKey2, "valueY");

        resetConfigurationHandler();

        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertEquals(configurationHandler.get(systemKey1, "qqq"), "newvalue1");
        assertEquals(configurationHandler.get(systemKey2, "qqq"), "newvalue2");
    }

    private void resetConfigurationHandler() throws Exception {
        resetConfigurationHandler(new ConfigurationHandlerImpl(m_context));
    }

    private void resetConfigurationHandler(ConfigurationHandler configurationHandler) throws Exception {
        ConfigurationHandler oldConfigurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);
        if (oldConfigurationHandler instanceof ComponentBase) {
            ((ComponentBase) oldConfigurationHandler).stop();
        }

        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
    }
}
