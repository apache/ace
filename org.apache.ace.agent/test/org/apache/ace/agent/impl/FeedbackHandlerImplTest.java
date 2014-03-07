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

import static org.apache.ace.agent.AgentConstants.CONFIG_FEEDBACK_CHANNELS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.osgi.framework.BundleContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Testing {@link FeedbackHandlerImplTest}.
 */
public class FeedbackHandlerImplTest extends BaseAgentTest {
    private static final String AUDITLOG = InternalConstants.AUDITLOG_FEEDBACK_CHANNEL;
    private static final String CUSTOMCHANNEL = "customchannel";
    private static final String NON_EXISTING_CHANNEL = "nonExistingChannel";

    private static final String AUDITLOG_AND_CUSTOMCHANNEL = AUDITLOG + "," + CUSTOMCHANNEL;

    private AgentContextImpl m_agentContextImpl;

    @BeforeClass
    public void setUpAgain() throws Exception {
        m_agentContextImpl = mockAgentContext();
        replayTestMocks();

        BundleContext bc = mockBundleContext();

        m_agentContextImpl.setHandler(FeedbackHandler.class, new FeedbackHandlerImpl());
        m_agentContextImpl.setHandler(EventsHandler.class, new EventsHandlerImpl(bc));
        m_agentContextImpl.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl(bc));

        m_agentContextImpl.start();
    }

    @AfterClass
    public void tearDownAgain() throws Exception {
        m_agentContextImpl.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    /**
     * Tests that there is always a default channel registered when starting the agent.
     */
    @Test
    public void testDefaultFeedbackChannelPresent() throws Exception {
        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG);
        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG);

        assertFeedbackChannelsNotPresent(feedbackHandler, NON_EXISTING_CHANNEL);
    }

    @Test
    public void testSingleFeedbackChannelConfig() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, CONFIG_FEEDBACK_CHANNELS, AUDITLOG);

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG);
        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG);
    }

    @Test
    public void testUpdateConfigAddFeedbackChannel() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, CONFIG_FEEDBACK_CHANNELS, AUDITLOG);

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG);

        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG);
        assertFeedbackChannelsNotPresent(feedbackHandler, CUSTOMCHANNEL);

        configureAgent(configurationHandler, CONFIG_FEEDBACK_CHANNELS, AUDITLOG_AND_CUSTOMCHANNEL);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG, CUSTOMCHANNEL);

        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG, CUSTOMCHANNEL);
    }

    @Test
    public void testUpdateConfigRemoveFeedbackChannel() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContextImpl.getHandler(ConfigurationHandler.class);

        configureAgent(configurationHandler, CONFIG_FEEDBACK_CHANNELS, AUDITLOG_AND_CUSTOMCHANNEL);

        FeedbackHandler feedbackHandler = m_agentContextImpl.getHandler(FeedbackHandler.class);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG, CUSTOMCHANNEL);
        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG, CUSTOMCHANNEL);

        configureAgent(configurationHandler, CONFIG_FEEDBACK_CHANNELS, AUDITLOG);

        assertFeedbackChannelNames(feedbackHandler, AUDITLOG);
        assertFeedbackChannelsPresent(feedbackHandler, AUDITLOG);

        assertFeedbackChannelsNotPresent(feedbackHandler, CUSTOMCHANNEL);
    }

    private void assertFeedbackChannelNames(FeedbackHandler handler, String... names) throws IOException {
        Set<String> availableNames = handler.getChannelNames();
        assertNotNull(availableNames);
        assertEquals(availableNames.size(), names.length);
        for (String name : names) {
            assertTrue(availableNames.contains(name), "Expected channel '" + name + "' to be present!");
        }
    }

    private void assertFeedbackChannelsPresent(FeedbackHandler handler, String... names) throws IOException {
        for (String name : names) {
            assertNotNull(handler.getChannel(name), "Expected channel '" + name + "' to be present!");
        }
    }

    private void assertFeedbackChannelsNotPresent(FeedbackHandler handler, String... names) throws IOException {
        for (String name : names) {
            assertNull(handler.getChannel(name), "Expected channel '" + name + "' NOT to be present!");
        }
    }
}
