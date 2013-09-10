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

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;

/**
 * Implementation of the public agent control service.
 */
public class AgentControlImpl implements AgentControl {
    private final AgentContext m_agentContext;

    public AgentControlImpl(AgentContext agentContext) throws IOException {
        m_agentContext = agentContext;
    }

    @Override
    public String getAgentId() {
        return m_agentContext.getHandler(IdentificationHandler.class).getAgentId();
    }

    @Override
    public ConfigurationHandler getConfigurationHandler() {
        return m_agentContext.getHandler(ConfigurationHandler.class);
    }

    @Override
    public DeploymentHandler getDeploymentHandler() {
        return m_agentContext.getHandler(DeploymentHandler.class);
    }

    @Override
    public AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentContext.getHandler(AgentUpdateHandler.class);
    }

    @Override
    public FeedbackHandler getFeedbackHandler() {
        return m_agentContext.getHandler(FeedbackHandler.class);
    }
}
