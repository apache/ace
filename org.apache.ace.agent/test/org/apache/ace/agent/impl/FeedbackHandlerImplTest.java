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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link FeedbackHandlerImplTest}.
 */
public class FeedbackHandlerImplTest extends BaseAgentTest {

    private AgentContext m_agentContext;
    private ConfigurationHandler m_configurationHandler;

    @BeforeMethod
    public void setUpAgain(Method method) throws Exception {
        File methodDir = new File(new File(getWorkDir(), FeedbackHandlerImplTest.class.getName()), method.getName());
        methodDir.mkdirs();
        cleanDir(methodDir);

        m_agentContext = addTestMock(AgentContext.class);
        m_configurationHandler = addTestMock(ConfigurationHandler.class);
        expect(m_agentContext.getWorkDir()).andReturn(methodDir).anyTimes();
        expect(m_agentContext.getConfigurationHandler()).andReturn(m_configurationHandler).anyTimes();
        replayTestMocks();
    }

    @AfterMethod
    public void tearDownAgain(Method method) throws Exception {
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testFeedbackChannelConfig() throws Exception {

        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(FeedbackHandlerImpl.CONFIG_KEY_CHANNELS), anyObject(String.class))).andReturn("auditlog").anyTimes();
        replay(m_configurationHandler);

        FeedbackHandler feedbackHandler = new FeedbackHandlerImpl();
        startHandler(feedbackHandler, m_agentContext);

        Set<String> names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertTrue(names.size() == 1);
        assertTrue(names.contains("auditlog"));
        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNull(feedbackHandler.getChannel("QQQ"));

        reset(m_configurationHandler);
        expect(m_configurationHandler.get(eq(FeedbackHandlerImpl.CONFIG_KEY_CHANNELS), anyObject(String.class))).andReturn("auditlog, customchannel").anyTimes();
        replay(m_configurationHandler);

        names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertTrue(names.size() == 2);
        assertTrue(names.contains("auditlog"));
        assertTrue(names.contains("customchannel"));
        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNotNull(feedbackHandler.getChannel("customchannel"));
        assertNull(feedbackHandler.getChannel("QQQ"));
    }
}
