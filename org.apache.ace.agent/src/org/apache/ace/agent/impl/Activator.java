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
    private final InternalEventAdmin m_internalEventAdmin = new InternalEventAdmin();
    private final InternalLogService m_internalLogService = new InternalLogService();

    // managed state
    private AgentContext m_agentContext;
    private AgentControl m_agentControl;
    private ScheduledExecutorService m_executorService;
    private AgentUpdateHandlerImpl m_agentUpdateHandler; // we use the implementation type here on purpose
    private DeploymentAdmin m_internalDeploymentAdmin;
    private Component m_agentControlComponent = null;
    private Component m_defaultControllerComponent = null;
    private EventLoggerImpl m_eventLoggerImpl;

    // injected services
    private volatile PackageAdmin m_packageAdmin;
    private volatile EventAdmin m_externalEventAdmin;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        m_executorService = Executors.newScheduledThreadPool(1, new InternalThreadFactory());

        m_internalDeploymentAdmin = new DeploymentAdminImpl();
        configureField(m_internalDeploymentAdmin, BundleContext.class, context);
        configureField(m_internalDeploymentAdmin, PackageAdmin.class, null);
        configureField(m_internalDeploymentAdmin, EventAdmin.class, m_internalEventAdmin);
        configureField(m_internalDeploymentAdmin, LogService.class, m_internalLogService);

        m_agentContext = new AgentContextImpl(context.getDataFile(""));
        m_agentControl = new AgentControlImpl(m_agentContext);
        m_agentUpdateHandler = new AgentUpdateHandlerImpl(m_agentContext, context);

        configureField(m_agentContext, AgentControl.class, m_agentControl);
        configureField(m_agentContext, EventAdmin.class, m_internalEventAdmin);
        configureField(m_agentContext, LogService.class, m_internalLogService);
        configureField(m_agentContext, ConfigurationHandler.class, new ConfigurationHandlerImpl(m_agentContext));
        configureField(m_agentContext, ConnectionHandler.class, new ConnectionHandlerImpl(m_agentContext));
        configureField(m_agentContext, DeploymentHandler.class, new DeploymentHandlerImpl(m_agentContext, m_internalDeploymentAdmin));
        configureField(m_agentContext, DiscoveryHandler.class, new DiscoveryHandlerImpl(m_agentContext));
        configureField(m_agentContext, DownloadHandler.class, new DownloadHandlerImpl(m_agentContext));
        configureField(m_agentContext, IdentificationHandler.class, new IdentificationHandlerImpl(m_agentContext));
        configureField(m_agentContext, ScheduledExecutorService.class, m_executorService);
        configureField(m_agentContext, AgentUpdateHandler.class, m_agentUpdateHandler);

        Component agentContextComponent = createComponent()
            .setImplementation(m_agentContext)
            .setCallbacks(this, null, "startAgent", "stopAgent", null)
            .setAutoConfig(BundleContext.class, false)
            .setAutoConfig(DependencyManager.class, false)
            .setAutoConfig(Component.class, false)
            .add(createServiceDependency()
                .setService(PackageAdmin.class).setRequired(true)
                .setCallbacks(this, "packageAdminAdded", "packageAdminRemoved"))
            .add(createServiceDependency()
                .setService(EventAdmin.class).setRequired(false)
                .setCallbacks(this, "eventAdminAdded", "eventAdminRemoved"));

        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.identificationhandler.disabled"))) {
            m_internalLogService.log(LogService.LOG_INFO, "Initializing agent...");
            agentContextComponent.add(createServiceDependency().setService(IdentificationHandler.class).setRequired(true));
        }
        // FIXME fake config
        if (Boolean.parseBoolean(System.getProperty("agent.discoveryhandler.disabled"))) {
            m_internalLogService.log(LogService.LOG_INFO, "Initializing agent...");
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

    synchronized void eventAdminAdded(EventAdmin eventAdmin) {
        if (m_externalEventAdmin == null) {
            m_externalEventAdmin = eventAdmin;
            configureField(m_internalEventAdmin, EventAdmin.class, eventAdmin);
        }
    }

    synchronized void eventAdminRemoved(EventAdmin eventAdmin) {
        if (m_externalEventAdmin == eventAdmin) {
            m_externalEventAdmin = null;
            configureField(m_internalEventAdmin, EventAdmin.class, null);
        }
    }

    void startAgent() throws Exception {

        m_internalLogService.log(LogService.LOG_INFO, "Starting agent...");
        invokeMethod(m_internalDeploymentAdmin, "start", new Class<?>[] {}, new Object[] {});
        invokeMethod(m_agentContext, "start", new Class<?>[] {}, new Object[] {});

        m_internalLogService.log(LogService.LOG_DEBUG, "* agent control service registered");
        m_agentControlComponent = createComponent()
            .setInterface(AgentControl.class.getName(), null)
            .setImplementation(m_agentControl);
        getDependencyManager().add(m_agentControlComponent);
        // FIXME fake config
        if (!Boolean.parseBoolean(System.getProperty("agent.defaultcontroller.disabled"))) {
            // FIXME move to agentcontext constructor
            DefaultController defaultController = new DefaultController(m_agentContext);
            m_defaultControllerComponent = createComponent()
                .setImplementation(defaultController);
            getDependencyManager().add(m_defaultControllerComponent);
            m_internalLogService.log(LogService.LOG_DEBUG, "* default controller registered");
        }
        else {
            m_internalLogService.log(LogService.LOG_DEBUG, "* default controller disabled");
        }
        // FIXME fake config
        if (!Boolean.parseBoolean(System.getProperty("agent.auditlogging.disabled"))) {
            m_eventLoggerImpl = new EventLoggerImpl(m_agentControl, getDependencyManager().getBundleContext());
            BundleContext bundleContext = getDependencyManager().getBundleContext();
            bundleContext.addBundleListener(m_eventLoggerImpl);
            bundleContext.addFrameworkListener(m_eventLoggerImpl);
            m_internalEventAdmin.registerHandler(m_eventLoggerImpl, EventLoggerImpl.TOPICS_INTEREST);
            m_internalLogService.log(LogService.LOG_DEBUG, "* auditlog listener registered");
        }
        else {
            m_internalLogService.log(LogService.LOG_DEBUG, "* auditlog listener disabled");
        }
        // at this point we know the agent has started, so any updater bundle that
        // might still be running can be uninstalled
        // FIXME move to handlers own life cycle
        m_agentUpdateHandler.uninstallUpdaterBundle();
        m_internalLogService.log(LogService.LOG_INFO, "Agent started!");
    }

    void stopAgent() throws Exception {

        m_internalLogService.log(LogService.LOG_INFO, "Stopping agent...");
        if (m_agentControlComponent != null) {
            getDependencyManager().remove(m_agentControlComponent);
            m_agentControlComponent = null;
        }
        if (m_defaultControllerComponent != null) {
            getDependencyManager().remove(m_defaultControllerComponent);
            m_defaultControllerComponent = null;
        }
        if (m_eventLoggerImpl != null) {
            BundleContext bundleContext = getDependencyManager().getBundleContext();
            bundleContext.removeFrameworkListener(m_eventLoggerImpl);
            bundleContext.removeBundleListener(m_eventLoggerImpl);
            m_internalEventAdmin.unregisterHandler(m_eventLoggerImpl);
        }

        invokeMethod(m_internalDeploymentAdmin, "stop", new Class<?>[] {}, new Object[] {});
        m_internalLogService.log(LogService.LOG_INFO, "Agent stopped!");
    }

    static class InternalEventAdmin implements EventAdmin {

        private final Map<EventHandler, String[]> m_eventHandlers = new HashMap<EventHandler, String[]>();
        private volatile EventAdmin m_eventAdmin;

        @Override
        public void postEvent(Event event) {
            sendInternal(event);
            EventAdmin eventAdmin = m_eventAdmin;
            if (eventAdmin != null)
                eventAdmin.postEvent(event);
        }

        @Override
        public void sendEvent(Event event) {
            sendInternal(event);
            EventAdmin eventAdmin = m_eventAdmin;
            if (eventAdmin != null)
                eventAdmin.sendEvent(event);
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
    }

    static class InternalLogService implements LogService {

        private static String getName(int level) {
            switch (level) {
                case 1:
                    return "[ERROR  ] ";
                case 2:
                    return "[WARNING] ";
                case 3:
                    return "[INFO   ] ";
                case 4:
                    return "[DEBUG  ] ";
                default:
                    throw new IllegalStateException("Unknown level: " + level);
            }
        }

        @Override
        public void log(int level, String message) {
            System.out.println(getName(level) + message);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            System.out.println(getName(level) + message);
            exception.printStackTrace(System.out);
        }

        @Override
        public void log(ServiceReference sr, int level, String message) {
            System.out.println(getName(level) + message);
        }

        @Override
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            System.out.println(getName(level) + message);
            exception.printStackTrace(System.out);
        }
    }

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
