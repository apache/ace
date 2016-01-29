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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
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

    private final Map<Class<?>, Object> m_handlers;
    private final Set<Object> m_components;
    private final AtomicReference<Object> m_controllerRef;
    private final Semaphore m_semaphore;
    private final File m_workDir;

    private volatile Future<?> m_future;

    public AgentContextImpl(File workDir) {
        m_workDir = workDir;

        m_semaphore = new Semaphore(1);
        m_handlers = new HashMap<>();
        m_components = new LinkedHashSet<>();
        m_controllerRef = new AtomicReference<>();
    }

    /**
     * Adds a component to this context.
     * 
     * @param component
     *            The component to add, cannot be <code>null</code>.
     */
    public void addComponent(Object component) {
        m_components.add(component);
    }

    @Override
    public <T> T getHandler(Class<T> iface) {
        Object result = m_handlers.get(iface);
        return iface.cast(result);
    }

    @Override
    public File getWorkDir() {
        return m_workDir;
    }

    /**
     * Sets the controller to use for the agent.
     * 
     * @param controller
     *            the controller to use, cannot be <code>null</code>.
     */
    public void setController(Object controller) {
        Object old;
        do {
            old = m_controllerRef.get();
        }
        while (!m_controllerRef.compareAndSet(old, controller));

        if (old != null) {
            stopController(old);
        }
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
     * Start the context.
     * 
     * @throws Exception
     *             On failure.
     */
    public void start() throws Exception {
        m_semaphore.acquire();
        try {
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
            // Ensure the handlers are started in a deterministic order...
            for (Class<?> handlerIface : KNOWN_HANDLERS) {
                Object handler = m_handlers.get(handlerIface);
                startAgentContextAware(handler);
            }
            for (Object component : m_components) {
                startAgentContextAware(component);
            }

            // Lastly, start the agent controller...
            Object controller = m_controllerRef.get();
            if (controller != null) {
                startController(controller);
            }
        }
        finally {
            m_semaphore.release();
        }
    }

    /**
     * Stop the context.
     * 
     * @throws Exception
     *             On failure.
     */
    public void stop() throws Exception {
        m_semaphore.acquire();
        try {
            // First, stop the agent controller...
            Object controller = m_controllerRef.get();
            if (controller != null) {
                stopController(controller);
            }

            for (Object component : m_components) {
                stopAgentContextAware(component);
            }
            for (int i = (KNOWN_HANDLERS.length - 1); i >= 0; i--) {
                Class<?> iface = KNOWN_HANDLERS[i];
                Object handler = m_handlers.get(iface);
                stopAgentContextAware(handler);
            }
        }
        finally {
            // We do *not* allow the handlers/components to be reused...
            m_handlers.clear();
            m_components.clear();
            m_controllerRef.set(null);

            m_semaphore.release();
        }
    }

    private void initAgentContextAware(Object object) {
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

    private void startController(final Object object) {
        terminateRunningController();

        initAgentContextAware(object);

        // In case of a Runnable, we start a separate thread that executes this task...
        if (object instanceof Runnable) {
            ScheduledExecutorService executorService = getHandler(ScheduledExecutorService.class);
            if (executorService == null || executorService.isShutdown()) {
                return;
            }

            m_future = executorService.submit(new Runnable() {
                private static final String NAME = "ACE Agent Controller";

                @Override
                public void run() {
                    // Annotate the name of the thread for debugging purposes...
                    Thread.currentThread().setName(NAME);

                    startAgentContextAware(object);

                    ((Runnable) object).run();

                    stopAgentContextAware(object);
                }
            });
        }
        else {
            // Expect the controller to handle its own execution...
            startAgentContextAware(object);
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

    private void stopController(Object object) {
        terminateRunningController();

        if (!(object instanceof Runnable)) {
            stopAgentContextAware(object);
        }
    }

    /**
     * Terminates any running controller (if any).
     */
    private void terminateRunningController() {
        if (m_future != null) {
            m_future.cancel(true /* mayInterruptWhileRunning */);
            m_future = null;
        }
    }
}
