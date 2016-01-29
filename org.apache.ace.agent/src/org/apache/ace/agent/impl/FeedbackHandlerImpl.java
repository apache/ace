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

import static org.apache.ace.agent.AgentConstants.EVENT_AGENT_CONFIG_CHANGED;
import static org.apache.ace.agent.AgentConstants.CONFIG_FEEDBACK_CHANNELS;
import static org.apache.ace.agent.impl.InternalConstants.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;

/**
 * Default implementation of the feedback handler.
 */
public class FeedbackHandlerImpl extends ComponentBase implements FeedbackHandler, EventListener {
    private static Set<String> split(String value) {
        Set<String> trimmedValues = new HashSet<>();
        if (value != null) {
            String[] rawValues = value.split(",");
            for (String rawValue : rawValues) {
                trimmedValues.add(rawValue.trim());
            }
        }
        return trimmedValues;
    }

    private final ConcurrentMap<String, FeedbackChannelImpl> m_channels;

    public FeedbackHandlerImpl() {
        super("feedback");

        m_channels = new ConcurrentHashMap<>();
    }

    @Override
    public FeedbackChannel getChannel(String name) throws IOException {
        return m_channels.get(name);
    }

    @Override
    public Set<String> getChannelNames() throws IOException {
        return m_channels.keySet();
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            String value = payload.get(CONFIG_FEEDBACK_CHANNELS);
            if (value != null && !"".equals(value.trim())) {
                Set<String> seen = new HashSet<>(m_channels.keySet());

                Set<String> channelNames = split(value);
                if (channelNames.containsAll(seen) && seen.containsAll(channelNames)) {
                    // Nothing to do...
                    return;
                }

                for (String channelName : channelNames) {
                    try {
                        registerFeedbackChannel(channelName);
                        seen.remove(channelName);
                    }
                    catch (IOException exception) {
                        logError("Failed to created feedback channel for '%s'", exception, channelName);
                    }
                }

                for (String oldChannelName : seen) {
                    try {
                        unregisterFeedbackChannel(oldChannelName);
                    }
                    catch (IOException exception) {
                        logError("Failed to close feedback channel for '%s'", exception, oldChannelName);
                    }
                }
            }
        }
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStart() throws Exception {
        // Make sure the default audit log is present...
        registerFeedbackChannel(AUDITLOG_FEEDBACK_CHANNEL);
    }

    @Override
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);

        for (String channelName : getChannelNames()) {
            try {
                unregisterFeedbackChannel(channelName);
            }
            catch (IOException exception) {
                logWarning("Failed to close feedback channel '%s'", exception, channelName);
            }
        }
    }

    private void registerFeedbackChannel(String channelName) throws IOException {
        FeedbackChannelImpl channel = new FeedbackChannelImpl(getAgentContext(), channelName);
        m_channels.putIfAbsent(channelName, channel);
    }

    private void unregisterFeedbackChannel(String oldChannelName) throws IOException {
        FeedbackChannelImpl channel = m_channels.remove(oldChannelName);
        if (channel != null) {
            channel.stop();
        }
    }
}
