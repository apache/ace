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
import org.apache.ace.agent.impl.DependencyTrackerImpl.LifecycleCallbacks;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * 
 */
public class Activator implements BundleActivator {

    // managed state
    private volatile AgentContextImpl m_agentContext;
    private volatile ScheduledExecutorService m_executorService;
    private volatile ServiceRegistration m_agentControlRegistration;
    private volatile BundleContext m_bundleContext;
    private volatile DependencyTrackerImpl m_dependencyTracker;

    // injected services
    private volatile PackageAdmin m_packageAdmin;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile ConnectionHandler m_connectionHandler;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {

        m_bundleContext = bundleContext;
        m_executorService = Executors.newScheduledThreadPool(1, new InternalThreadFactory());

        // FIXME minimize
        m_dependencyTracker = new DependencyTrackerImpl(bundleContext, new LifecycleCallbacks() {

            @Override
            public void started() {
                try {
                    startAgent();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void stopped() {
                try {
                    stopAgent();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        m_dependencyTracker.addDependency(PackageAdmin.class, null, new DependencyCallback() {
            @Override
            public void updated(Object service) {
                m_packageAdmin = (PackageAdmin) service;
            }
        });

        if (Boolean.parseBoolean(System.getProperty(AgentConstants.CONFIG_IDENTIFICATION_DISABLED))) {
            m_dependencyTracker.addDependency(IdentificationHandler.class, null, new DependencyCallback() {
                @Override
                public void updated(Object service) {
                    m_identificationHandler = (IdentificationHandler) service;
                }
            });
        }

        if (Boolean.parseBoolean(System.getProperty(AgentConstants.CONFIG_DISCOVERY_DISABLED))) {
            m_dependencyTracker.addDependency(DiscoveryHandler.class, null, new DependencyCallback() {
                @Override
                public void updated(Object service) {
                    m_discoveryHandler = (DiscoveryHandler) service;
                }
            });
        }

        if (Boolean.parseBoolean(System.getProperty(AgentConstants.CONFIG_CONNECTION_DISABLED))) {
            m_dependencyTracker.addDependency(ConnectionHandler.class, null, new DependencyCallback() {
                @Override
                public void updated(Object service) {
                    m_connectionHandler = (ConnectionHandler) service;
                }
            });
        }

        m_dependencyTracker.startTracking();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        m_dependencyTracker.stopTracking();
        m_executorService.shutdownNow();
        m_executorService = null;
    }

    private void startAgent() throws Exception {

        m_agentContext = new AgentContextImpl(m_bundleContext.getDataFile(""));
        m_agentContext.setHandler(LoggingHandler.class, new LoggingHandlerImpl());
        m_agentContext.setHandler(ConfigurationHandler.class, new ConfigurationHandlerImpl());
        m_agentContext.setHandler(EventsHandler.class, new EventsHandlerImpl(m_bundleContext));
        m_agentContext.setHandler(ScheduledExecutorService.class, m_executorService);
        m_agentContext.setHandler(DownloadHandler.class, new DownloadHandlerImpl());
        m_agentContext.setHandler(DeploymentHandler.class, new DeploymentHandlerImpl(m_bundleContext, m_packageAdmin));
        m_agentContext.setHandler(AgentUpdateHandler.class, new AgentUpdateHandlerImpl(m_bundleContext));
        m_agentContext.setHandler(FeedbackHandler.class, new FeedbackHandlerImpl());

        if (m_identificationHandler != null) {
            m_agentContext.setHandler(IdentificationHandler.class, m_identificationHandler);
        }
        else {
            m_agentContext.setHandler(IdentificationHandler.class, new IdentificationHandlerImpl());
        }

        if (m_discoveryHandler != null) {
            m_agentContext.setHandler(DiscoveryHandler.class, m_discoveryHandler);
        }
        else {
            m_agentContext.setHandler(DiscoveryHandler.class, new DiscoveryHandlerImpl());
        }

        if (m_connectionHandler != null) {
            m_agentContext.setHandler(ConnectionHandler.class, m_connectionHandler);
        }
        else {
            m_agentContext.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        }

        m_agentContext.addComponent(new DefaultController());
        m_agentContext.addComponent(new EventLoggerImpl(m_bundleContext));
        m_agentContext.start();

        m_agentControlRegistration = m_bundleContext.registerService(
            AgentControl.class.getName(), new AgentControlImpl(m_agentContext), null);
    }

    private void stopAgent() throws Exception {
        m_agentControlRegistration.unregister();
        m_agentControlRegistration = null;
        m_agentContext.stop();
        m_agentContext = null;
    }

    /**
     * Internal thread factory that assigns recognizable names to the threads it creates and sets them in daemon mode.
     */
    static class InternalThreadFactory implements ThreadFactory {

        private static final String m_name = "ACE Agent worker (%s)";
        private int m_count = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, String.format(m_name, ++m_count));
            thread.setDaemon(true);
            return thread;
        }
    }
}
