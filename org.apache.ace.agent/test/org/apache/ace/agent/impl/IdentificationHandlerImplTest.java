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

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Testing {@link IdentificationHandlerImpl}.
 */
public class IdentificationHandlerImplTest extends BaseAgentTest {

    private AgentContextImpl m_agentContextImpl;
    private AgentContext m_agentContext;

    @BeforeClass
    public void setUpAgain() throws Exception {
        m_agentContextImpl = mockAgentContext();
        m_agentContext = m_agentContextImpl;
        m_agentContextImpl.setHandler(IdentificationHandler.class, new IdentificationHandlerImpl());
        m_agentContextImpl.start();
        replayTestMocks();
    }

    @AfterClass
    public void tearDownAgain() throws Exception {
        m_agentContextImpl.stop();
        verifyTestMocks();
    }

    @Test
    public void testAvailableIdentification() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_IDENTIFICATION_AGENTID), anyObject(String.class)))
            .andReturn("qqq").once();
        replay(configurationHandler);
        IdentificationHandler identificationHandler = m_agentContext.getHandler(IdentificationHandler.class);
        assertEquals(identificationHandler.getAgentId(), "qqq");
    }

    @Test
    public void testUpdatedIdentification() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_IDENTIFICATION_AGENTID), anyObject(String.class)))
            .andReturn("qqq").once();
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_IDENTIFICATION_AGENTID), anyObject(String.class)))
            .andReturn("yyy").once();
        replay(configurationHandler);
        IdentificationHandler identificationHandler = m_agentContext.getHandler(IdentificationHandler.class);
        assertEquals(identificationHandler.getAgentId(), "qqq");
        assertEquals(identificationHandler.getAgentId(), "yyy");
    }

    @Test
    public void testNoIdentification() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_IDENTIFICATION_AGENTID), anyObject(String.class)))
            .andReturn(null).once();
        replay(configurationHandler);
        IdentificationHandler identificationHandler = m_agentContext.getHandler(IdentificationHandler.class);
        assertNull(identificationHandler.getAgentId());
    }

    @Test
    public void testEmptyIdentification() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        reset(configurationHandler);
        expect(configurationHandler.get(eq(AgentConstants.CONFIG_IDENTIFICATION_AGENTID), anyObject(String.class)))
            .andReturn(null).once();
        replay(configurationHandler);
        IdentificationHandler identificationHandler = m_agentContext.getHandler(IdentificationHandler.class);
        assertNull(identificationHandler.getAgentId());
    }
}
