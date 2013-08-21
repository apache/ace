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

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Testing {@link IdentificationHandlerImpl}.
 */
public class IdentificationHandlerImplTest extends BaseAgentTest {

    private IdentificationHandler m_identificationHandler;
    private ConfigurationHandler m_configurationHandler;

    @BeforeTest
    public void setUpAgain() throws Exception {
        AgentContext agentContext = addTestMock(AgentContext.class);
        m_identificationHandler = new IdentificationHandlerImpl(agentContext);
        m_configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(agentContext.getConfigurationHandler()).andReturn(m_configurationHandler).anyTimes();
        replayTestMocks();
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        verifyTestMocks();
    }

    @Test
    public void testAvailableIdentification() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(IdentificationHandlerImpl.CONFIG_KEY_IDENTIFICATION), anyObject(String.class)))
            .andReturn("qqq").once();
        replay(m_configurationHandler);
        assertEquals(m_identificationHandler.getAgentId(), "qqq");
    }

    @Test
    public void testUpdatedIdentification() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(IdentificationHandlerImpl.CONFIG_KEY_IDENTIFICATION), anyObject(String.class)))
            .andReturn("qqq").once();
        expect(m_configurationHandler.get(eq(IdentificationHandlerImpl.CONFIG_KEY_IDENTIFICATION), anyObject(String.class)))
            .andReturn("yyy").once();
        replay(m_configurationHandler);
        assertEquals(m_identificationHandler.getAgentId(), "qqq");
        assertEquals(m_identificationHandler.getAgentId(), "yyy");
    }

    @Test
    public void testNoIdentification() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(IdentificationHandlerImpl.CONFIG_KEY_IDENTIFICATION), anyObject(String.class)))
            .andReturn(null).once();
        replay(m_configurationHandler);
        assertNull(m_identificationHandler.getAgentId());
    }

    @Test
    public void testEmptyIdentification() throws Exception {
        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(IdentificationHandlerImpl.CONFIG_KEY_IDENTIFICATION), anyObject(String.class)))
            .andReturn(null).once();
        replay(m_configurationHandler);
        assertNull(m_identificationHandler.getAgentId());
    }
}
