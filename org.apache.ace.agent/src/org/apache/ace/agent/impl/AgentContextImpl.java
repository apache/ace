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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * Implementation of the internal agent context service.
 */
public class AgentContextImpl implements AgentContext {

    // List of required handler in startup order
    public static final Class<?>[] KNOWN_HANDLERS = new Class<?>[]
    {
        LoggingHandler.class,
        ConfigurationHandler.class,
        IdentificationHandler.class,
        DiscoveryHandler.class,
        DeploymentHandler.class,
        DownloadHandler.class,
        ConnectionHandler.class,
        AgentUpdateHandler.class,
        FeedbackHandler.class,
        EventsHandler.class,
        ScheduledExecutorService.class
    };

    private final Map<Class<?>, Object> m_handlers = new HashMap<Class<?>, Object>();
    private final Set<Object> m_components = new HashSet<Object>();
    private final File m_workDir;

    public AgentContextImpl(File workDir) {
        m_workDir = workDir;
    }

    /**
     * Start the context.
     * 
     * @throws Exception
     *             On failure.
     */
    public void start() throws Exception {
        // Make sure the agent-context is set for all known handlers before they are started, this way we can ensure
        // they can properly call each other in their onStart() methods...
        for (Class<?> handlerIface : KNOWN_HANDLERS) {
            Object handler = m_handlers.get(handlerIface);
            if (handler == null) {
                throw new IllegalStateException("Can not start context. Missing handler: " + handlerIface.getName());
            }
            initAgentContextAware(handler);
        }
        for (Object component : m_components) {
            initAgentContextAware(component);
        }
        for (Object handler : m_handlers.values()) {
            startAgentContextAware(handler);
        }
        for (Object component : m_components) {
            startAgentContextAware(component);
        }
    }

    /**
     * Stop the context.
     * 
     * @throws Exception
     *             On failure.
     */
    public void stop() throws Exception {
        for (Object component : m_components) {
            stopAgentContextAware(component);
        }
        for (int i = (KNOWN_HANDLERS.length - 1); i >= 0; i--) {
            Class<?> iface = KNOWN_HANDLERS[i];
            Object handler = m_handlers.get(iface);
            stopAgentContextAware(handler);
        }
    }

    @Override
    public File getWorkDir() {
        return m_workDir;
    }

    @Override
    public <T> T getHandler(Class<T> iface) {
        Object result = m_handlers.get(iface);
        return iface.cast(result);
    }

    /**
     * Set a handler on the context.
     * 
     * @param iface
     *            The handler interface
     * @param handler
     *            The handler implementation
     */
    public <T> void setHandler(Class<T> iface, T handler) {
        m_handlers.put(iface, handler);
    }

    /**
     * Add a component on the context. 
     * 
     * @param component
     *            The component
     */
    public void addComponent(AgentContextAware component) {
        m_components.add(component);
    }

    private void initAgentContextAware(Object object) throws Exception {
        if (object instanceof AgentContextAware) {
            try {
                ((AgentContextAware) object).init(this);
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void startAgentContextAware(Object object) {
        if (object instanceof AgentContextAware) {
            try {
                ((AgentContextAware) object).start(this);
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void stopAgentContextAware(Object object) {
        if (object instanceof AgentContextAware) {
            try {
                ((AgentContextAware) object).stop();
            }
            catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }
}
