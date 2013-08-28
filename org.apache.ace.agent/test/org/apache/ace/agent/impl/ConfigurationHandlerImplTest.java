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
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Method;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link ConfigurationHandlerImpl}.
 */
public class ConfigurationHandlerImplTest extends BaseAgentTest {

    private AgentContextImpl m_agentContextImpl;

    @BeforeMethod
    public void setUpAgain(Method method) throws Exception {
        m_agentContextImpl = mockAgentContext(method.getName());
        replayTestMocks();
    }

    @AfterMethod
    public void tearDownAgain(Method method) throws Exception {
        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testConfigClean() throws Exception {

        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl();
        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(0, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get("key1", "default1"), "default1");

        // should be persisted
        configurationHandler = new ConfigurationHandlerImpl();
        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(0, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get("key1", "default1"), "default1");
    }

    @Test
    public void testConfigSystemProps() throws Exception {

        String systemKey1 = AgentConstants.CONFIG_KEY_NAMESPACE + "key1";
        String systemKey2 = AgentConstants.CONFIG_KEY_NAMESPACE + "key2";

        System.setProperty(systemKey1, "value1");
        System.setProperty(systemKey2, "value2");

        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl();
        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(2, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get(systemKey1, "qqq"), "value1");
        assertEquals(configurationHandler.get(systemKey2, "qqq"), "value2");

        // System props should be persisted

        System.clearProperty(systemKey1);
        System.clearProperty(systemKey2);

        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(2, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get(systemKey1, "qqq"), "value1");
        assertEquals(configurationHandler.get(systemKey2, "qqq"), "value2");

        // System props should override by default

        System.setProperty(systemKey1, "value1");
        System.setProperty(systemKey2, "value2");

        configurationHandler.put(systemKey1, "newvalue1");
        configurationHandler.put(systemKey2, "newvalue2");

        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(2, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get(systemKey1, "qqq"), "value1");
        assertEquals(configurationHandler.get(systemKey2, "qqq"), "value2");

        // System props should not override if retain is set

        System.setProperty(systemKey1, "valueX");
        System.setProperty(systemKey2, "valueY");
        System.setProperty(systemKey1 + AgentConstants.CONFIG_KEY_RETAIN, "true");
        System.setProperty(systemKey2 + AgentConstants.CONFIG_KEY_RETAIN, "true");

        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        assertNotNull(configurationHandler.keySet());
        assertEquals(2, configurationHandler.keySet().size());
        assertEquals(configurationHandler.get(systemKey1, "qqq"), "value1");
        assertEquals(configurationHandler.get(systemKey2, "qqq"), "value2");
    }

    @Test
    public void testConfigBooleanProps() throws Exception {

        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl();
        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configurationHandler.putBoolean("boolean1", true);
        configurationHandler.putBoolean("boolean2", false);

        assertEquals(configurationHandler.getBoolean("boolean1", false), true);
        assertEquals(configurationHandler.getBoolean("boolean2", true), false);

        assertEquals(configurationHandler.getBoolean("booleanX", true), true);
        assertEquals(configurationHandler.getBoolean("booleanY", false), false);
    }

    @Test
    public void testConfigLongProps() throws Exception {

        ConfigurationHandler configurationHandler = new ConfigurationHandlerImpl();
        m_agentContextImpl.stop();
        m_agentContextImpl.setHandler(ConfigurationHandler.class, configurationHandler);
        m_agentContextImpl.start();
        configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configurationHandler.putLong("long1", 42);
        configurationHandler.putLong("long2", 4l);

        assertEquals(configurationHandler.getLong("long1", 0l), 42);
        assertEquals(configurationHandler.getLong("long2", 0l), 4l);

        assertEquals(configurationHandler.getLong("longX", 42l), 42l);
    }
}
