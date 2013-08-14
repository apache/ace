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

import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class IdentificationhandlerImplTest extends BaseAgentTest {

    Map<String, String> m_configuration = new HashMap<String, String>();
    IdentificationHandler m_identificationHandler;

    @BeforeTest
    public void setUpAgain() throws Exception {
        AgentContext agentContext = addTestMock(AgentContext.class);
        m_identificationHandler = new IdentificationHandlerImpl(agentContext);
        ConfigurationHandler configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(configurationHandler.getMap()).andReturn(m_configuration).anyTimes();
        expect(agentContext.getConfigurationHandler()).andReturn(configurationHandler).anyTimes();
        replayTestMocks();
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        verifyTestMocks();
    }

    @Test
    public void testAvailableIdentification() throws Exception {
        m_configuration.put(IdentificationHandlerImpl.IDENTIFICATION_CONFIG_KEY, "qqq");
        assertEquals(m_identificationHandler.getIdentification(), "qqq");
    }

    @Test
    public void testUpdatedIdentification() throws Exception {
        m_configuration.put(IdentificationHandlerImpl.IDENTIFICATION_CONFIG_KEY, "qqq");
        assertEquals(m_identificationHandler.getIdentification(), "qqq");
        m_configuration.put(IdentificationHandlerImpl.IDENTIFICATION_CONFIG_KEY, "yyy");
        assertEquals(m_identificationHandler.getIdentification(), "yyy");
    }

    @Test
    public void testNoIdentification() throws Exception {
        m_configuration.clear();
        assertNull(m_identificationHandler.getIdentification());
    }

    @Test
    public void testEmptyIdentification() throws Exception {
        m_configuration.put(IdentificationHandlerImpl.IDENTIFICATION_CONFIG_KEY, " ");
        assertNull(m_identificationHandler.getIdentification());
    }
}
