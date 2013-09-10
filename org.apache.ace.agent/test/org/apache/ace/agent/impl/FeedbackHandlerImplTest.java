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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link FeedbackHandlerImplTest}.
 */
public class FeedbackHandlerImplTest extends BaseAgentTest {

    private AgentContextImpl m_agentContextImpl;

    @BeforeMethod
    public void setUpAgain(Method method) throws Exception {
        m_agentContextImpl = mockAgentContext(method.getName());
        replayTestMocks();

        m_agentContextImpl.setHandler(FeedbackHandler.class, new FeedbackHandlerImpl());
        m_agentContextImpl.setHandler(EventsHandler.class, new EventsHandlerImpl(mockBundleContext()));
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());

        m_agentContextImpl.start();
    }

    @AfterMethod
    public void tearDownAgain(Method method) throws Exception {
        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testSingleFeedbackChannelConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configurationHandler.put(AgentConstants.CONFIG_FEEDBACK_CHANNELS, "auditlog");

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        Set<String> names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertEquals(1, names.size());
        assertTrue(names.contains("auditlog"));

        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNull(feedbackHandler.getChannel("nonExistingChannel"));
    }

    @Test
    public void testUpdateConfigAddFeedbackChannel() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configurationHandler.put(AgentConstants.CONFIG_FEEDBACK_CHANNELS, "auditlog");

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        Set<String> names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertEquals(1, names.size());
        assertTrue(names.contains("auditlog"));

        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNull(feedbackHandler.getChannel("nonExistingChannel"));

        configurationHandler.put(AgentConstants.CONFIG_FEEDBACK_CHANNELS, "auditlog, customchannel");

        names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("auditlog"));
        assertTrue(names.contains("customchannel"));

        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNotNull(feedbackHandler.getChannel("customchannel"));
        assertNull(feedbackHandler.getChannel("nonExistingChannel"));
    }

    @Test
    public void testUpdateConfigRemoveFeedbackChannel() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configurationHandler.put(AgentConstants.CONFIG_FEEDBACK_CHANNELS, "auditlog, customchannel");

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        Set<String> names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("auditlog"));
        assertTrue(names.contains("customchannel"));

        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNotNull(feedbackHandler.getChannel("customchannel"));
        assertNull(feedbackHandler.getChannel("nonExistingChannel"));

        configurationHandler.put(AgentConstants.CONFIG_FEEDBACK_CHANNELS, "auditlog");

        names = feedbackHandler.getChannelNames();
        assertNotNull(names);
        assertEquals(1, names.size());
        assertTrue(names.contains("auditlog"));
        assertFalse(names.contains("customchannel"));

        assertNotNull(feedbackHandler.getChannel("auditlog"));
        assertNull(feedbackHandler.getChannel("customchannel"));
        assertNull(feedbackHandler.getChannel("nonExistingChannel"));
    }
}
