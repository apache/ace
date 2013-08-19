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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.FeedbackChannel;

public class AgentControlImpl implements AgentControl {

    private final AgentContext m_agentContext;

    private final Map<String, FeedbackChannelImpl> m_feedbackChannels = new HashMap<String, FeedbackChannelImpl>();

    public AgentControlImpl(AgentContext agentContext) throws IOException {
        m_agentContext = agentContext;
        // TODO get from configuration
        m_feedbackChannels.put("auditlog", new FeedbackChannelImpl(m_agentContext, "auditlog"));
    }

    @Override
    public ConfigurationHandler getConfiguration() {
        return m_agentContext.getConfigurationHandler();
    }

    @Override
    public DownloadHandler getDownloadHandler() {
        return m_agentContext.getDownloadHandler();
    }

    @Override
    public DeploymentHandler getDeploymentHandler() {
        return m_agentContext.getDeploymentHandler();
    }

    @Override
    public List<String> getFeedbackChannelNames() {
        // TODO get from configuration
        List<String> channels = new ArrayList<String>();
        channels.addAll(m_feedbackChannels.keySet());
        return channels;
    }

    @Override
    public FeedbackChannel getFeedbackChannel(String name) {
        return m_feedbackChannels.get(name);
    }

    @Override
    public AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentContext.getAgentUpdateHandler();
    }
}
