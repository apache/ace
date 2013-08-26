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

import static org.apache.ace.agent.impl.ReflectionUtil.configureField;
import static org.apache.ace.agent.impl.ReflectionUtil.invokeMethod;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.impl.DependencyTrackerImpl.DependencyCallback;
import org.apache.ace.agent.impl.DependencyTrackerImpl.LifecycleCallbacks;
import org.apache.felix.deploymentadmin.DeploymentAdminImpl;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * 
 */
public class Activator implements BundleActivator {

    // internal delegates
    private final EventsHandlerImpl m_internalEvents = new EventsHandlerImpl();
    private final LoggingHandlerImpl m_internalLogger = new LoggingHandlerImpl(LogService.LOG_DEBUG);

    // managed state
    private AgentContextImpl m_agentContext;
    private AgentControlImpl m_agentControl;
    private ScheduledExecutorService m_executorService;
    private EventLoggerImpl m_eventLoggerImpl;
    private DefaultController m_defaultController;
    private volatile DeploymentAdmin m_deploymentAdmin;
    private ServiceRegistration m_agentControlRegistration;

    // injected services
    private volatile PackageAdmin m_packageAdmin;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile ConnectionHandler m_connectionHandler;

    private volatile BundleContext m_bundleContext;
    private DependencyTrackerImpl m_dependencyTracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {

        m_bundleContext = bundleContext;
        m_executorService = Executors.newScheduledThreadPool(1, new InternalThreadFactory());

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

        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.identificationhandler.disabled"))) {
            m_dependencyTracker.addDependency(IdentificationHandler.class, null, new DependencyCallback() {
                @Override
                public void updated(Object service) {
                    m_identificationHandler = (IdentificationHandler) service;
                }
            });
        }
        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.discoveryhandler.disabled"))) {
            m_dependencyTracker.addDependency(DiscoveryHandler.class, null, new DependencyCallback() {
                @Override
                public void updated(Object service) {
                    m_discoveryHandler = (DiscoveryHandler) service;
                }
            });
        }
        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.connectionhandler.disabled"))) {
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

    void startAgent() throws Exception {

        m_internalLogger.logInfo("activator", "Agent starting...", null);

        m_deploymentAdmin = new DeploymentAdminImpl();
        configureField(m_deploymentAdmin, BundleContext.class, m_bundleContext);
        configureField(m_deploymentAdmin, PackageAdmin.class, m_packageAdmin);
        configureField(m_deploymentAdmin, EventAdmin.class, new InternalEventAdmin(m_internalEvents));
        configureField(m_deploymentAdmin, LogService.class, new InternalLogService(m_internalLogger, "deployment"));
        invokeMethod(m_deploymentAdmin, "start", new Class<?>[] {}, new Object[] {});

        m_agentContext = new AgentContextImpl(m_bundleContext.getDataFile(""));
        m_agentContext.setLoggingHandler(m_internalLogger);
        m_agentContext.setEventsHandler(m_internalEvents);
        m_agentContext.setConfigurationHandler(new ConfigurationHandlerImpl());
        m_agentContext.setExecutorService(m_executorService);
        m_agentContext.setConnectionHandler(new ConnectionHandlerImpl());
        m_agentContext.setIdentificationHandler(new IdentificationHandlerImpl());
        m_agentContext.setDiscoveryHandler(new DiscoveryHandlerImpl());
        m_agentContext.setDownloadHandler(new DownloadHandlerImpl());
        m_agentContext.setDeploymentHandler(new DeploymentHandlerImpl(m_deploymentAdmin));
        m_agentContext.setAgentUpdateHandler(new AgentUpdateHandlerImpl(m_bundleContext));
        m_agentContext.setFeedbackHandler(new FeedbackHandlerImpl());
        m_agentContext.start();
        m_internalLogger.logInfo("activator", "AgentContext started", null);

        m_agentControl = new AgentControlImpl(m_agentContext);
        m_agentControlRegistration = m_bundleContext.registerService(AgentControl.class.getName(), m_agentControl, null);
        m_internalLogger.logInfo("activator", "AgentControl registered", null);

        m_defaultController = new DefaultController();
        m_defaultController.start(m_agentContext);
        m_internalLogger.logInfo("activator", "DefaultController started", null);

        // FIXME fake config
        if (!Boolean.parseBoolean(System.getProperty("agent.auditlogging.disabled"))) {
            m_eventLoggerImpl = new EventLoggerImpl(m_agentControl, m_bundleContext);
            m_bundleContext.addBundleListener(m_eventLoggerImpl);
            m_bundleContext.addFrameworkListener(m_eventLoggerImpl);
            m_internalEvents.registerHandler(m_eventLoggerImpl, EventLoggerImpl.TOPICS_INTEREST);
            m_internalLogger.logInfo("activator", "Audit logger started", null);
        }
        else {
            m_internalLogger.logInfo("activator", "Audit logger disabled", null);
        }
        m_internalLogger.logInfo("activator", "Agent statup complete", null);
    }

    void stopAgent() throws Exception {

        m_internalLogger.logInfo("activator", "Agent stopping..", null);

        m_agentControlRegistration.unregister();
        m_agentControlRegistration = null;

        m_defaultController.stop();
        m_defaultController = null;

        invokeMethod(m_deploymentAdmin, "stop", new Class<?>[] {}, new Object[] {});

        if (m_eventLoggerImpl != null) {
            m_bundleContext.removeFrameworkListener(m_eventLoggerImpl);
            m_bundleContext.removeBundleListener(m_eventLoggerImpl);
            m_internalEvents.unregisterHandler(m_eventLoggerImpl);
        }

        m_agentContext.stop();
        m_agentContext = null;

        m_internalLogger.logInfo("activator", "Agent stopped", null);
    }

    private void configureDeploymentAdmin() {
        m_deploymentAdmin = new DeploymentAdminImpl();
        configureField(m_deploymentAdmin, BundleContext.class, m_bundleContext);
        configureField(m_deploymentAdmin, PackageAdmin.class, m_packageAdmin);
        configureField(m_deploymentAdmin, EventAdmin.class, new InternalEventAdmin(m_internalEvents));
        configureField(m_deploymentAdmin, LogService.class, new InternalLogService(m_internalLogger, "deployment"));
        invokeMethod(m_deploymentAdmin, "start", new Class<?>[] {}, new Object[] {});
    }

    /**
     * Internal EventAdmin that delegates to actual InternalEvents. Used to inject into the DeploymentAdmin only.
     */
    static class InternalEventAdmin implements EventAdmin {

        private final EventsHandler m_events;

        public InternalEventAdmin(EventsHandler events) {
            m_events = events;
        }

        @Override
        public void postEvent(Event event) {
            m_events.postEvent(event.getTopic(), getPayload(event));
        }

        @Override
        public void sendEvent(Event event) {
            m_events.postEvent(event.getTopic(), getPayload(event));
        }

        private static Dictionary<String, String> getPayload(Event event) {
            Dictionary<String, String> payload = new Hashtable<String, String>();
            for (String propertyName : event.getPropertyNames()) {
                payload.put(propertyName, event.getProperty(propertyName).toString());
            }
            return payload;
        }
    }

    /**
     * Internal LogService that wraps delegates to actual InternalLogger. Used to inject into the DeploymentAdmin only.
     */
    static class InternalLogService implements LogService {

        private final LoggingHandler m_logger;
        private final String m_identifier;

        public InternalLogService(LoggingHandler logger, String identifier) {
            m_logger = logger;
            m_identifier = identifier;
        }

        @Override
        public void log(int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            switch (level) {
                case LogService.LOG_WARNING:
                    m_logger.logWarning(m_identifier, message, exception);
                    return;
                case LogService.LOG_INFO:
                    m_logger.logInfo(m_identifier, message, exception);
                    return;
                case LogService.LOG_DEBUG:
                    m_logger.logDebug(m_identifier, message, exception);
                    return;
                default:
                    m_logger.logError(m_identifier, message, exception);
                    return;
            }
        }

        @Override
        public void log(ServiceReference sr, int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            log(level, message, exception);
        }
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
            return thread;
        }
    }

}
