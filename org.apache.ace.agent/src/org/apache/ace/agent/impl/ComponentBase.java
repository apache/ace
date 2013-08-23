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

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;

/**
 * Convenience implementation base class for all {@link AgentContextAware} components, such as handlers & controllers.
 * 
 */
public abstract class ComponentBase implements AgentContextAware {

    private final String m_componentIdentifier;
    private AgentContext m_agentContext;

    public ComponentBase(String handlerIdentifier) {
        m_componentIdentifier = handlerIdentifier;
    }

    @Override
    public final void start(AgentContext agentContext) throws Exception {
        m_agentContext = agentContext;
        m_agentContext.logDebug(m_componentIdentifier, "Starting");
        onStart();
    }

    @Override
    public final void stop() throws Exception {
        m_agentContext.logDebug(m_componentIdentifier, "Stopping");
        m_agentContext = null;
        onStop();
    }

    protected final AgentContext getAgentContext() {
        if (m_agentContext == null)
            throw new IllegalStateException("Handler is not started");
        return m_agentContext;
    }

    protected void onStart() throws Exception {
    }

    protected void onStop() throws Exception {
    }

    protected final IdentificationHandler getIdentificationHandler() {
        return m_agentContext.getIdentificationHandler();
    }

    protected final DiscoveryHandler getDiscoveryHandler() {
        return m_agentContext.getDiscoveryHandler();
    }

    protected final ConnectionHandler getConnectionHandler() {
        return m_agentContext.getConnectionHandler();
    }

    protected final DeploymentHandler getDeploymentHandler() {
        return m_agentContext.getDeploymentHandler();
    }

    protected final DownloadHandler getDownloadHandler() {
        return m_agentContext.getDownloadHandler();
    }

    protected final ConfigurationHandler getConfigurationHandler() {
        return m_agentContext.getConfigurationHandler();
    }

    protected final AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentContext.getAgentUpdateHandler();
    }

    protected final FeedbackHandler getFeedbackHandler() {
        return m_agentContext.getFeedbackHandler();
    }

    protected final ScheduledExecutorService getExecutorService() {
        return m_agentContext.getExecutorService();
    }

    protected final File getWorkDir() {
        return m_agentContext.getWorkDir();
    }

    protected final void logDebug(String message, Object... args) {
        getAgentContext().logDebug(m_componentIdentifier, message, null, args);
    }

    protected final void logDebug(String message, Throwable cause, Object... args) {
        getAgentContext().logDebug(m_componentIdentifier, message, cause, args);
    }

    protected final void logInfo(String message, Object... args) {
        getAgentContext().logInfo(m_componentIdentifier, message, null, args);
    }

    protected final void logInfo(String message, Throwable cause, Object... args) {
        getAgentContext().logInfo(m_componentIdentifier, message, cause, args);
    }

    protected final void logWarning(String message, Object... args) {
        getAgentContext().logWarning(m_componentIdentifier, message, null, args);
    }

    protected final void logWarning(String message, Throwable cause, Object... args) {
        getAgentContext().logWarning(m_componentIdentifier, message, cause, args);
    }

    protected final void logError(String message, Object... args) {
        getAgentContext().logError(m_componentIdentifier, message, null, args);
    }

    protected final void logError(String message, Throwable cause, Object... args) {
        getAgentContext().logError(m_componentIdentifier, message, cause, args);
    }
}
