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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
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
import org.apache.ace.agent.impl.DependencyTrackerImpl.DependencyCallback;
import org.apache.ace.agent.impl.DependencyTrackerImpl.LifecycleCallback;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Bundle activator for ACE management agent.
 */
@SuppressWarnings("deprecation")
public class Activator implements BundleActivator, LifecycleCallback {
    // managed state
    private volatile AgentContextImpl m_agentContext;
    private volatile ScheduledExecutorService m_executorService;
    private volatile ServiceRegistration<?> m_agentControlRegistration;
    private volatile DependencyTrackerImpl m_dependencyTracker;
    private volatile Object m_controller;

    // injected services
    private volatile PackageAdmin m_packageAdmin;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile ConnectionHandler m_connectionHandler;

    /**
     * Called by OSGi framework when starting this bundle. It will start a bare dependency manager for tracking several
     * of the (configurable) services. Once all dependencies are satisfied, {@link #componentStarted(BundleContext)}
     * will be called.
     */
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        // Essentially a two-threaded executor with scheduling support, one thread is "reserved" for the controller...
        m_executorService = new ScheduledThreadPoolExecutor(2 /* core pool size */, new InternalThreadFactory());

        m_dependencyTracker = new DependencyTrackerImpl(bundleContext, this);

        addPackageAdminDependency(m_dependencyTracker);

        if (getBoolean(bundleContext, AgentConstants.CONFIG_IDENTIFICATION_DISABLED)) {
            addIdentificationHandlerDependency(m_dependencyTracker);
        }

        if (getBoolean(bundleContext, AgentConstants.CONFIG_DISCOVERY_DISABLED)) {
            addDiscoveryHandlerDependency(m_dependencyTracker);
        }

        if (getBoolean(bundleContext, AgentConstants.CONFIG_CONNECTION_DISABLED)) {
            addConnectionHandlerDependency(m_dependencyTracker);
        }

        // Create the controller in this method will ensure that if this fails, this bundle is *not* started...
        m_controller = createAgentController(bundleContext);

        m_dependencyTracker.startTracking();
    }

    /**
     * Called by OSGi framework when stopping this bundle.
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            m_dependencyTracker.stopTracking();
        }
        finally {
            m_executorService.shutdownNow();
            m_executorService = null;
        }
    }

    /**
     * Called by our {@link DependencyTrackerImpl} when all dependencies are satisfied.
     */
    public void componentStarted(BundleContext context) throws Exception {
        final File bundleDataArea = context.getDataFile("");

        m_agentContext = new AgentContextImpl(bundleDataArea);

        m_agentContext.setHandler(LoggingHandler.class, new LoggingHandlerImpl(context));
        m_agentContext.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl(context));
        m_agentContext.setHandler(EventsHandler.class, new EventsHandlerImpl(context));
        m_agentContext.setHandler(ScheduledExecutorService.class, m_executorService);
        m_agentContext.setHandler(DownloadHandler.class, new DownloadHandlerImpl(bundleDataArea));
        m_agentContext.setHandler(DeploymentHandler.class, new DeploymentHandlerImpl(context, m_packageAdmin));
        m_agentContext.setHandler(AgentUpdateHandler.class, new AgentUpdateHandlerImpl(context));
        m_agentContext.setHandler(FeedbackHandler.class, new FeedbackHandlerImpl());

        IdentificationHandler identificationHandler = (m_identificationHandler != null) ? m_identificationHandler : new IdentificationHandlerImpl();
        m_agentContext.setHandler(IdentificationHandler.class, identificationHandler);

        DiscoveryHandler discoveryHandler = (m_discoveryHandler != null) ? m_discoveryHandler : new DiscoveryHandlerImpl();
        m_agentContext.setHandler(DiscoveryHandler.class, discoveryHandler);

        ConnectionHandler connectionHandler = (m_connectionHandler != null) ? m_connectionHandler : new ConnectionHandlerImpl();
        m_agentContext.setHandler(ConnectionHandler.class, connectionHandler);

        m_agentContext.addComponent(new EventLoggerImpl(context));

        // Lastly, inject the (custom) controller for this agent...
        m_agentContext.setController(m_controller);

        m_agentContext.start();

        m_agentControlRegistration = context.registerService(AgentControl.class.getName(), new AgentControlImpl(m_agentContext), null);
    }

    /**
     * Called by our {@link DependencyTrackerImpl} when one or more dependencies are no longer satisfied.
     */
    public void componentStopped(BundleContext context) throws Exception {
        try {
            if (m_agentControlRegistration != null) {
                m_agentControlRegistration.unregister();
            }
        }
        finally {
            m_agentControlRegistration = null;

            try {
                m_agentContext.stop();
            }
            finally {
                m_agentContext = null;
            }
        }
    }

    private void addConnectionHandlerDependency(DependencyTrackerImpl tracker) throws Exception {
        tracker.addDependency(ConnectionHandler.class, null, new DependencyCallback() {
            @Override
            public void updated(Object service) {
                m_connectionHandler = (ConnectionHandler) service;
            }
        });
    }

    private void addDiscoveryHandlerDependency(DependencyTrackerImpl tracker) throws Exception {
        tracker.addDependency(DiscoveryHandler.class, null, new DependencyCallback() {
            @Override
            public void updated(Object service) {
                m_discoveryHandler = (DiscoveryHandler) service;
            }
        });
    }

    private void addIdentificationHandlerDependency(DependencyTrackerImpl tracker) throws Exception {
        tracker.addDependency(IdentificationHandler.class, null, new DependencyCallback() {
            @Override
            public void updated(Object service) {
                m_identificationHandler = (IdentificationHandler) service;
            }
        });
    }

    private void addPackageAdminDependency(DependencyTrackerImpl tracker) throws Exception {
        tracker.addDependency(PackageAdmin.class, null, new DependencyCallback() {
            @Override
            public void updated(Object service) {
                m_packageAdmin = (PackageAdmin) service;
            }
        });
    }

    /**
     * Factory method for creating the agent controller.
     * 
     * @param context
     *            the bundle context to use, cannot be <code>null</code>.
     * @return a controller instance, never <code>null</code>.
     * @throws ClassNotFoundException
     *             in case a custom controller class was defined, but this class could not be loaded;
     * @throws IllegalAccessException
     *             in case a custom controller class was defined, but did not have a public default constructor;
     * @throws InstantiationException
     *             in case a custom controller class was defined, but instantiation lead to an exception.
     */
    private Object createAgentController(BundleContext context) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        String controllerName = context.getProperty(AgentConstants.CONFIG_CONTROLLER_CLASS);
        if (controllerName != null) {
            Class<?> controllerClass = context.getBundle().loadClass(controllerName);
            return controllerClass.newInstance();
        }
        return new DefaultController();
    }

    /**
     * Retrieves the bundle (or system) property with the given name and returns <code>true</code> iff the value of that
     * property is "true" (case insensitive).
     * 
     * @param bundleContext
     *            the bundle context to use;
     * @param propertyName
     *            the name of the boolean property to retrieve.
     * @return <code>true</code> iff the value of the property was "true" (case insenstive), <code>false</code>
     *         otherwise.
     */
    private static boolean getBoolean(BundleContext bundleContext, String propertyName) {
        return Boolean.parseBoolean(bundleContext.getProperty(propertyName));
    }

    /**
     * Internal thread factory that assigns recognizable names to the threads it creates and sets them in daemon mode.
     */
    public static class InternalThreadFactory implements ThreadFactory {
        private static final String NAME_TPL = "ACE Agent Worker %s";
        private final AtomicInteger m_count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, String.format(NAME_TPL, m_count.incrementAndGet()));
        }
    }
}
