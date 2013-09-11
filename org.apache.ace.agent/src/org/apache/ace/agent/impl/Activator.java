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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
public class Activator implements BundleActivator, LifecycleCallback {
    // managed state
    private volatile AgentContextImpl m_agentContext;
    private volatile ScheduledExecutorService m_executorService;
    private volatile ServiceRegistration m_agentControlRegistration;
    private volatile DependencyTrackerImpl m_dependencyTracker;

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
        m_executorService = Executors.newScheduledThreadPool(5, new InternalThreadFactory());

        m_dependencyTracker = new DependencyTrackerImpl(bundleContext, this);

        addPackageAdminDependency(m_dependencyTracker);

        if (Boolean.getBoolean(AgentConstants.CONFIG_IDENTIFICATION_DISABLED)) {
            addIdenticationHandlerDependency(m_dependencyTracker);
        }

        if (Boolean.getBoolean(AgentConstants.CONFIG_DISCOVERY_DISABLED)) {
            addDiscoveryHandlerDependency(m_dependencyTracker);
        }

        if (Boolean.getBoolean(AgentConstants.CONFIG_CONNECTION_DISABLED)) {
            addConnectionHandlerDependency(m_dependencyTracker);
        }

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
        m_agentContext = new AgentContextImpl(context.getDataFile(""));

        m_agentContext.setHandler(LoggingHandler.class, new LoggingHandlerImpl());
        m_agentContext.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());
        m_agentContext.setHandler(EventsHandler.class, new EventsHandlerImpl(context));
        m_agentContext.setHandler(ScheduledExecutorService.class, m_executorService);
        m_agentContext.setHandler(DownloadHandler.class, new DownloadHandlerImpl());
        m_agentContext.setHandler(DeploymentHandler.class, new DeploymentHandlerImpl(context, m_packageAdmin));
        m_agentContext.setHandler(AgentUpdateHandler.class, new AgentUpdateHandlerImpl(context));
        m_agentContext.setHandler(FeedbackHandler.class, new FeedbackHandlerImpl());
        
        IdentificationHandler identificationHandler = (m_identificationHandler != null) ? m_identificationHandler : new IdentificationHandlerImpl();
        m_agentContext.setHandler(IdentificationHandler.class, identificationHandler);

        DiscoveryHandler discoveryHandler = (m_discoveryHandler != null) ? m_discoveryHandler : new DiscoveryHandlerImpl();
        m_agentContext.setHandler(DiscoveryHandler.class, discoveryHandler);

        ConnectionHandler connectionHandler = (m_connectionHandler != null) ? m_connectionHandler : new ConnectionHandlerImpl();
        m_agentContext.setHandler(ConnectionHandler.class, connectionHandler);

        m_agentContext.addComponent(new DefaultController());
        m_agentContext.addComponent(new EventLoggerImpl(context));
        
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

    private void addIdenticationHandlerDependency(DependencyTrackerImpl tracker) throws Exception {
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
     * Internal thread factory that assigns recognizable names to the threads it creates and sets them in daemon mode.
     */
    public static class InternalThreadFactory implements ThreadFactory {
        private static final String NAME_TPL = "ACE Agent worker (%s)";
        private final AtomicInteger m_count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, String.format(NAME_TPL, m_count.incrementAndGet()));
            // TODO JaWi: is this really what we want? This means that these threads can be
            // shutdown without any means to cleanup (can cause I/O errors, file corruption,
            // a new world order, ...)
            thread.setDaemon(true);
            // TODO JaWi: shouldn't we set the uncaught exception handler for these kind of
            // threads? It would allow us to explicitly log something when things go wrong...
            return thread;
        }
    }
}
