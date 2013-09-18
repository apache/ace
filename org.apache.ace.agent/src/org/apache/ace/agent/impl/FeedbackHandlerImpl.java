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
    private final ConcurrentMap<String, FeedbackChannelImpl> m_channels;

    public FeedbackHandlerImpl() {
        super("feedback");

        m_channels = new ConcurrentHashMap<String, FeedbackChannelImpl>();
    }

    @Override
    public Set<String> getChannelNames() throws IOException {
        return m_channels.keySet();
    }

    @Override
    public FeedbackChannel getChannel(String name) throws IOException {
        return m_channels.get(name);
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            String value = payload.get(CONFIG_FEEDBACK_CHANNELS);
            if (value != null && !"".equals(value.trim())) {
                Set<String> seen = new HashSet<String>(m_channels.keySet());

                Set<String> channelNames = split(value);
                if (channelNames.containsAll(seen) && seen.containsAll(channelNames)) {
                    // Nothing to do...
                    return;
                }

                for (String channelName : channelNames) {
                    try {
                        m_channels.putIfAbsent(channelName, new FeedbackChannelImpl(getAgentContext(), channelName));
                        seen.remove(channelName);
                    }
                    catch (IOException exception) {
                        logError("Failed to created feedback channel for '%s'", exception, channelName);
                    }
                }

                for (String oldChannelName : seen) {
                    FeedbackChannelImpl channel = m_channels.remove(oldChannelName);
                    try {
                        channel.closeStore();
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
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);

        m_channels.clear();
    }

    private static Set<String> split(String value) {
        Set<String> trimmedValues = new HashSet<String>();
        if (value != null) {
            String[] rawValues = value.split(",");
            for (String rawValue : rawValues) {
                trimmedValues.add(rawValue.trim());
            }
        }
        return trimmedValues;
    }
}
