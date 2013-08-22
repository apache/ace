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

import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.felix.deploymentadmin.DeploymentAdminImpl;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;

// TODO Decouple from DM to save 170k in agent size. Or: just include what we use
public class Activator extends DependencyActivatorBase {

    // internal delegates
    private final InternalEvents m_internalEvents = new InternalEvents();
    private final InternalLogger m_internalLogger = new InternalLogger(1);

    // managed state
    private AgentContextImpl m_agentContext;
    private AgentControl m_agentControl;
    private ScheduledExecutorService m_executorService;
    private AgentUpdateHandlerImpl m_agentUpdateHandler; // we use the implementation type here on purpose
    private DeploymentAdmin m_internalDeploymentAdmin;
    private Component m_agentControlComponent = null;
    private EventLoggerImpl m_eventLoggerImpl;
    private DefaultController m_defaultController;

    // injected services
    private volatile PackageAdmin m_packageAdmin;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        m_executorService = Executors.newScheduledThreadPool(1, new InternalThreadFactory());

        m_internalDeploymentAdmin = new DeploymentAdminImpl();
        configureField(m_internalDeploymentAdmin, BundleContext.class, context);
        configureField(m_internalDeploymentAdmin, PackageAdmin.class, null);
        configureField(m_internalDeploymentAdmin, EventAdmin.class, new InternalEventAdmin(m_internalEvents));
        configureField(m_internalDeploymentAdmin, LogService.class, new InternalLogService(m_internalLogger, "deployment"));

        m_agentContext = new AgentContextImpl(context.getDataFile(""), m_internalLogger, m_internalEvents);
        m_agentControl = new AgentControlImpl(m_agentContext);
        m_agentUpdateHandler = new AgentUpdateHandlerImpl(context);

        // TODO replace with setters
        configureField(m_agentContext, AgentControl.class, m_agentControl);
        configureField(m_agentContext, ConfigurationHandler.class, new ConfigurationHandlerImpl());
        configureField(m_agentContext, ConnectionHandler.class, new ConnectionHandlerImpl());
        configureField(m_agentContext, DeploymentHandler.class, new DeploymentHandlerImpl(m_internalDeploymentAdmin));
        configureField(m_agentContext, DiscoveryHandler.class, new DiscoveryHandlerImpl());
        configureField(m_agentContext, DownloadHandler.class, new DownloadHandlerImpl());
        configureField(m_agentContext, IdentificationHandler.class, new IdentificationHandlerImpl());
        configureField(m_agentContext, ScheduledExecutorService.class, m_executorService);
        configureField(m_agentContext, AgentUpdateHandler.class, m_agentUpdateHandler);
        configureField(m_agentContext, FeedbackHandler.class, new FeedbackHandlerImpl());

        Component agentContextComponent = createComponent()
            .setImplementation(m_agentContext)
            .setCallbacks(this, null, "startAgent", "stopAgent", null)
            .setAutoConfig(BundleContext.class, false)
            .setAutoConfig(DependencyManager.class, false)
            .setAutoConfig(Component.class, false)
            .add(createServiceDependency()
                .setService(PackageAdmin.class).setRequired(true)
                .setCallbacks(this, "packageAdminAdded", "packageAdminRemoved"));

        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.identificationhandler.disabled"))) {
            m_internalLogger.logInfo("activator", "Initializing agent...", null);
            agentContextComponent.add(createServiceDependency().setService(IdentificationHandler.class).setRequired(true));
        }
        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.discoveryhandler.disabled"))) {
            m_internalLogger.logInfo("activator", "Initializing agent...", null);
            agentContextComponent.add(createServiceDependency().setService(DiscoveryHandler.class).setRequired(true));
        }
        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.connectionhandler.disabled"))) {
            agentContextComponent.add(createServiceDependency().setService(ConnectionHandler.class).setRequired(true));
        }
        manager.add(agentContextComponent);
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        m_executorService.shutdownNow();
        m_executorService = null;
    }

    synchronized void packageAdminAdded(PackageAdmin packageAdmin) {
        if (m_packageAdmin == null) {
            m_packageAdmin = packageAdmin;
            configureField(m_internalDeploymentAdmin, PackageAdmin.class, packageAdmin);
        }
    }

    synchronized void packageAdminRemoved(PackageAdmin packageAdmin) {
        if (m_packageAdmin == packageAdmin) {
            m_packageAdmin = null;
            configureField(m_internalDeploymentAdmin, PackageAdmin.class, null);
        }
    }

    void startAgent() throws Exception {

        m_internalLogger.logInfo("activator", "Agent starting...", null);

        invokeMethod(m_internalDeploymentAdmin, "start", new Class<?>[] {}, new Object[] {});
        m_agentContext.start();

        m_internalLogger.logInfo("activator", "Agent control service started", null);
        m_agentControlComponent = createComponent()
            .setInterface(AgentControl.class.getName(), null)
            .setImplementation(m_agentControl);
        getDependencyManager().add(m_agentControlComponent);

        // FIXME fake config
        if (!Boolean.parseBoolean(System.getProperty("agent.defaultcontroller.disabled"))) {
            m_defaultController = new DefaultController();
            m_defaultController.start(m_agentContext);
            m_internalLogger.logInfo("activator", "Default controller started", null);
        }
        else {
            m_internalLogger.logInfo("activator", "Default controller disabled", null);
        }

        // FIXME fake config
        if (!Boolean.parseBoolean(System.getProperty("agent.auditlogging.disabled"))) {
            m_eventLoggerImpl = new EventLoggerImpl(m_agentControl, getDependencyManager().getBundleContext());
            BundleContext bundleContext = getDependencyManager().getBundleContext();
            bundleContext.addBundleListener(m_eventLoggerImpl);
            bundleContext.addFrameworkListener(m_eventLoggerImpl);
            m_internalEvents.registerHandler(m_eventLoggerImpl, EventLoggerImpl.TOPICS_INTEREST);
            m_internalLogger.logInfo("activator", "Audit logger started", null);
        }
        else {
            m_internalLogger.logInfo("activator", "Audit logger disabled", null);
        }

        m_internalLogger.logInfo("activator", "Agent started", null);
    }

    void stopAgent() throws Exception {

        m_internalLogger.logInfo("activator", "Agent stopping..", null);

        if (m_agentControlComponent != null) {
            getDependencyManager().remove(m_agentControlComponent);
            m_agentControlComponent = null;
        }

        if (m_defaultController != null) {
            m_defaultController.stop();
            m_defaultController = null;
        }

        if (m_eventLoggerImpl != null) {
            BundleContext bundleContext = getDependencyManager().getBundleContext();
            bundleContext.removeFrameworkListener(m_eventLoggerImpl);
            bundleContext.removeBundleListener(m_eventLoggerImpl);
            m_internalEvents.unregisterHandler(m_eventLoggerImpl);
        }

        m_agentContext.stop();
        invokeMethod(m_internalDeploymentAdmin, "stop", new Class<?>[] {}, new Object[] {});
        m_internalLogger.logInfo("activator", "Agent stopped", null);
    }

    /**
     * InternalEvents that posts events to internal handlers and external admins.
     */
    static class InternalEvents {

        private final Map<EventHandler, String[]> m_eventHandlers = new HashMap<EventHandler, String[]>();

        public void postEvent(String topic, Dictionary<String, String> payload) {
            Event event = new Event(topic, payload);
            postEvent(event);
        }

        public void postEvent(Event event) {
            sendInternal(event);
            sendExternal(event);
        }

        void registerHandler(EventHandler eventHandler, String[] topics) {
            synchronized (m_eventHandlers) {
                m_eventHandlers.put(eventHandler, topics);
            }
        }

        void unregisterHandler(EventHandler eventHandler) {
            synchronized (m_eventHandlers) {
                m_eventHandlers.remove(eventHandler);
            }
        }

        private void sendInternal(Event event) {
            String topic = event.getTopic();
            synchronized (m_eventHandlers) {
                for (Entry<EventHandler, String[]> entry : m_eventHandlers.entrySet()) {
                    for (String interest : entry.getValue()) {
                        if ((interest.endsWith("*") && topic.startsWith(interest.substring(0, interest.length() - 1))
                        || topic.equals(interest))) {
                            entry.getKey().handleEvent(event);
                            break;
                        }
                    }
                }
            }
        }

        private void sendExternal(Event event) {
            // TODO this requires looking for all service references and invoking any found admins using reflection
        }

    }

    /**
     * Internal EventAdmin that delegates to actual InternalEvents. Used to inject into the DeploymentAdmin only.
     */
    static class InternalEventAdmin implements EventAdmin {

        private final InternalEvents m_events;

        public InternalEventAdmin(InternalEvents events) {
            m_events = events;
        }

        @Override
        public void postEvent(Event event) {
            m_events.postEvent(event);
        }

        @Override
        public void sendEvent(Event event) {
            m_events.postEvent(event);
        }
    }

    /**
     * Internal logger that writes to system out for now. It minimizes work until it is determined the loglevel is
     * loggable.
     */
    static class InternalLogger {

        private final int m_level;

        public InternalLogger(int level) {
            m_level = level;
        }

        private void log(String level, String component, String message, Throwable exception, Object... args) {
            if (args.length > 0)
                message = String.format(message, args);
            String line = String.format("[%s] %TT (%s) %s", level, new Date(), component, message);
            System.out.println(line);
            if (exception != null)
                exception.printStackTrace(System.out);
        }

        public void logDebug(String component, String message, Throwable exception, Object... args) {
            if (m_level > 1)
                return;
            log("DEBUG", component, message, exception, args);
        }

        public void logInfo(String component, String message, Throwable exception, Object... args) {
            if (m_level > 2)
                return;
            log("INFO", component, message, exception, args);
        }

        public void logWarning(String component, String message, Throwable exception, Object... args) {
            if (m_level > 3)
                return;
            log("WARNING", component, message, exception, args);
        }

        public void logError(String component, String message, Throwable exception, Object... args) {
            log("ERROR", component, message, exception, args);
        }
    }

    /**
     * Internal LogService that wraps delegates to actual InternalLogger. Used to inject into the DeploymentAdmin only.
     */
    static class InternalLogService implements LogService {

        private final InternalLogger m_logger;
        private final String m_identifier;

        public InternalLogService(InternalLogger logger, String identifier) {
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
                case 1:
                    m_logger.logDebug(m_identifier, message, exception);
                    return;
                case 2:
                    m_logger.logInfo(m_identifier, message, exception);
                    return;
                case 3:
                    m_logger.logWarning(m_identifier, message, exception);
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
