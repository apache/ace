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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.RetryAfterException;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EventLoggerImplTest extends BaseAgentTest {

    static class TestFeedbackChannel implements FeedbackChannel {

        int m_lastType = 0;

        @Override
        public void sendFeedback() throws RetryAfterException, IOException {
        }

        @Override
        public void write(int type, Map<String, String> properties) throws IOException {
            m_lastType = type;
        }

        public int getLastTtype() {
            return m_lastType;
        }

        public void reset() {
            m_lastType = 0;
        }
    }

    static class TestFeedbackHandler implements FeedbackHandler {

        Map<String, FeedbackChannel> channels = new HashMap<>();

        TestFeedbackHandler() {
            channels.put("auditlog", new TestFeedbackChannel());
        }

        @Override
        public Set<String> getChannelNames() throws IOException {
            return channels.keySet();
        }

        @Override
        public FeedbackChannel getChannel(String name) throws IOException {
            return channels.get("auditlog");
        }

    }

    private AgentContextImpl m_agentContext;
    private EventLoggerImpl m_eventLogger;
    private EventsHandler m_eventsHandler;

    @BeforeClass(alwaysRun = true)
    public void setUpOnceAgain() throws Exception {
        m_agentContext = mockAgentContext();

        BundleContext bc = mockBundleContext();

        m_eventsHandler = new EventsHandlerImpl(bc);
        m_agentContext.setHandler(EventsHandler.class, m_eventsHandler);
        m_agentContext.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl(bc));
        m_agentContext.setHandler(FeedbackHandler.class, new TestFeedbackHandler());

        replayTestMocks();
        m_agentContext.start();

    }

    @AfterClass(alwaysRun = true)
    public void tearDownOnceAgain() throws Exception {
        m_agentContext.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @BeforeMethod(alwaysRun = true)
    public void reset() throws Exception {
        // create a new eventlogger for every test
        m_eventLogger = new EventLoggerImpl(mockBundleContext());
        m_eventLogger.init(m_agentContext);
        m_eventsHandler.addListener(m_eventLogger);
        m_eventLogger.start(m_agentContext);

        // reset the previously logged data in our mock feedbackchannel. No need to create a new one here
        FeedbackHandler feedbackHandler = m_agentContext.getHandler(FeedbackHandler.class);
        TestFeedbackChannel channel = (TestFeedbackChannel) feedbackHandler.getChannel("auditlog");
        channel.reset();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testWriteEvent() throws Exception {
        FrameworkEvent event = new FrameworkEvent(32, new Object());
        m_eventLogger.frameworkEvent(event);

        FeedbackHandler feedbackHandler = m_agentContext.getHandler(FeedbackHandler.class);
        TestFeedbackChannel channel = (TestFeedbackChannel) feedbackHandler.getChannel("auditlog");
        assertEquals(channel.getLastTtype(), 1001);

    }

    @SuppressWarnings("deprecation")
    @Test
    public void testExcludeEvent() throws Exception {
        ConfigurationHandler configurationHandler = m_agentContext.getHandler(ConfigurationHandler.class);
        configureAgent(configurationHandler, AgentConstants.CONFIG_LOGGING_EXCLUDE_EVENTS, "1001,1002");

        FrameworkEvent event = new FrameworkEvent(32, new Object());

        FeedbackHandler feedbackHandler = m_agentContext.getHandler(FeedbackHandler.class);
        TestFeedbackChannel channel = (TestFeedbackChannel) feedbackHandler.getChannel("auditlog");
        // make sure the configuration is written to the channel
        assertEquals(channel.getLastTtype(), 2000);

        m_eventLogger.frameworkEvent(event);

        // make sure nothing is written to the channel
        assertEquals(channel.getLastTtype(), 2000);
    }
}
