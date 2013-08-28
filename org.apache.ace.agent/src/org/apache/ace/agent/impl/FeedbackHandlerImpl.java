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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;

/**
 * Default implementation of the feedback handler.
 */
public class FeedbackHandlerImpl extends ComponentBase implements FeedbackHandler {

    private final Map<String, FeedbackChannelImpl> m_channels = new HashMap<String, FeedbackChannelImpl>();
    private Set<String> m_channelNames;
    private String m_channelNamesConfig;

    public FeedbackHandlerImpl() {
        super("feedback");
    }

    @Override
    protected void onStart() throws Exception {
        synchronized (m_channels) {
            ensureChannels(); // fail fast
        }
    }

    @Override
    protected void onStop() throws Exception {
        synchronized (m_channels) {
            clearChannels();
        }
    }

    @Override
    public Set<String> getChannelNames() throws IOException {
        synchronized (m_channels) {
            ensureChannels();
            return m_channelNames;
        }
    }

    @Override
    public FeedbackChannel getChannel(String name) throws IOException {
        synchronized (m_channels) {
            ensureChannels();
            return m_channels.get(name);
        }
    }

    private void ensureChannels() throws IOException {
        String channelNamesConfig = getConfigurationHandler().get(CONFIG_FEEDBACK_CHANNELS, "auditlog");
        if (m_channelNamesConfig != null && m_channelNamesConfig.equals(channelNamesConfig)) {
            return;
        }

        m_channelNamesConfig = channelNamesConfig;
        m_channelNames = Collections.unmodifiableSet(getConfigurationValues(channelNamesConfig));
        m_channels.clear();
        for (String channelName : m_channelNames) {
            m_channels.put(channelName, new FeedbackChannelImpl(getAgentContext(), channelName));
        }
    }

    private void clearChannels() {
        m_channelNamesConfig = null;
        m_channelNames = null;
        m_channels.clear();
    }

    // TODO move to util or configurationhandler
    private static Set<String> getConfigurationValues(String value) {
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
