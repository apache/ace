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
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.LoggingHandler;

/**
 * Convenience implementation base class for all {@link AgentContextAware} components, such as handlers & controllers.
 */
public abstract class ComponentBase implements AgentContextAware {

    private final String m_identifier;
    private volatile AgentContext m_context;

    public ComponentBase(String handlerIdentifier) {
        m_identifier = handlerIdentifier;
    }

    @Override
    public final void start(AgentContext agentContext) throws Exception {
        if (agentContext == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        m_context = agentContext;
        onStart();
    }

    @Override
    public final void stop() throws Exception {
        onStop();
        m_context = null;
    }

    protected final AgentContext getAgentContext() {
        if (m_context == null)
            throw new IllegalStateException("Handler is not started: " + m_identifier);
        return m_context;
    }

    protected void onStart() throws Exception {
    }

    protected void onStop() throws Exception {
    }

    protected final IdentificationHandler getIdentificationHandler() {
        return m_context.getHandler(IdentificationHandler.class);
    }

    protected final DiscoveryHandler getDiscoveryHandler() {
        return m_context.getHandler(DiscoveryHandler.class);
    }

    protected final ConnectionHandler getConnectionHandler() {
        return m_context.getHandler(ConnectionHandler.class);
    }

    protected final DeploymentHandler getDeploymentHandler() {
        return m_context.getHandler(DeploymentHandler.class);
    }

    protected final DownloadHandler getDownloadHandler() {
        return m_context.getHandler(DownloadHandler.class);
    }

    protected final ConfigurationHandler getConfigurationHandler() {
        return m_context.getHandler(ConfigurationHandler.class);
    }

    protected final AgentUpdateHandler getAgentUpdateHandler() {
        return m_context.getHandler(AgentUpdateHandler.class);
    }

    protected final FeedbackHandler getFeedbackHandler() {
        return m_context.getHandler(FeedbackHandler.class);
    }

    protected final LoggingHandler getLoggingHandler() {
        return m_context.getHandler(LoggingHandler.class);
    }

    protected final EventsHandler getEventsHandler() {
        return m_context.getHandler(EventsHandler.class);
    }

    protected final ScheduledExecutorService getExecutorService() {
        return m_context.getHandler(ScheduledExecutorService.class);
    }

    protected final File getWorkDir() {
        return m_context.getWorkDir();
    }

    protected final void logDebug(String message, Object... args) {
        getLoggingHandler().logDebug(m_identifier, message, null, args);
    }

    protected final void logDebug(String message, Throwable cause, Object... args) {
        getLoggingHandler().logDebug(m_identifier, message, cause, args);
    }

    protected final void logInfo(String message, Object... args) {
        getLoggingHandler().logInfo(m_identifier, message, null, args);
    }

    protected final void logInfo(String message, Throwable cause, Object... args) {
        getLoggingHandler().logInfo(m_identifier, message, cause, args);
    }

    protected final void logWarning(String message, Object... args) {
        getLoggingHandler().logWarning(m_identifier, message, null, args);
    }

    protected final void logWarning(String message, Throwable cause, Object... args) {
        getLoggingHandler().logWarning(m_identifier, message, cause, args);
    }

    protected final void logError(String message, Object... args) {
        getLoggingHandler().logError(m_identifier, message, null, args);
    }

    protected final void logError(String message, Throwable cause, Object... args) {
        getLoggingHandler().logError(m_identifier, message, cause, args);
    }
}
