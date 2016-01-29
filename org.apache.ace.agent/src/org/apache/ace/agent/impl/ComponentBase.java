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
import java.util.concurrent.atomic.AtomicReference;

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
    // Injected by AgentContextImpl...
    private final AtomicReference<AgentContext> m_contextRef;

    public ComponentBase(String handlerIdentifier) {
        m_identifier = handlerIdentifier;
        m_contextRef = new AtomicReference<>();
    }

    @Override
    public final void init(AgentContext agentContext) throws Exception {
        if (agentContext == null) {
            throw new IllegalArgumentException("Context must not be null");
        }

        setAgentContext(agentContext);

        onInit();
    }

    @Override
    public final void start(AgentContext agentContext) throws Exception {
        if (agentContext == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        else if (getAgentContext() != agentContext) {
            // Just to be sure...
            throw new IllegalStateException("Context changed between init and start?!");
        }
        onStart();
    }

    @Override
    public final void stop() throws Exception {
        try {
            onStop();
        }
        finally {
            setAgentContext(null);
        }
    }

    protected final AgentContext getAgentContext() {
        AgentContext context = m_contextRef.get();
        if (context == null) {
            throw new IllegalStateException("Handler is not started: " + m_identifier);
        }
        return context;
    }

    protected void onInit() throws Exception {
        // Nop
    }

    protected void onStart() throws Exception {
        // Nop
    }

    protected void onStop() throws Exception {
        // Nop
    }

    protected final IdentificationHandler getIdentificationHandler() {
        return getAgentContext().getHandler(IdentificationHandler.class);
    }

    protected final DiscoveryHandler getDiscoveryHandler() {
        return getAgentContext().getHandler(DiscoveryHandler.class);
    }

    protected final ConnectionHandler getConnectionHandler() {
        return getAgentContext().getHandler(ConnectionHandler.class);
    }

    protected final DeploymentHandler getDeploymentHandler() {
        return getAgentContext().getHandler(DeploymentHandler.class);
    }

    protected final DownloadHandler getDownloadHandler() {
        return getAgentContext().getHandler(DownloadHandler.class);
    }

    protected final ConfigurationHandler getConfigurationHandler() {
        return getAgentContext().getHandler(ConfigurationHandler.class);
    }

    protected final AgentUpdateHandler getAgentUpdateHandler() {
        return getAgentContext().getHandler(AgentUpdateHandler.class);
    }

    protected final FeedbackHandler getFeedbackHandler() {
        return getAgentContext().getHandler(FeedbackHandler.class);
    }

    protected final LoggingHandler getLoggingHandler() {
        return getAgentContext().getHandler(LoggingHandler.class);
    }

    protected final EventsHandler getEventsHandler() {
        return getAgentContext().getHandler(EventsHandler.class);
    }

    protected final ScheduledExecutorService getExecutorService() {
        return getAgentContext().getHandler(ScheduledExecutorService.class);
    }

    protected final File getWorkDir() {
        return getAgentContext().getWorkDir();
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

    private void setAgentContext(AgentContext agentContext) {
        AgentContext old;
        do {
            old = m_contextRef.get();
        }
        while (!m_contextRef.compareAndSet(old, agentContext));
    }
}
