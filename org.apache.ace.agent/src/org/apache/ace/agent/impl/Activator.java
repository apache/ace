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
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

// TODO Decouple from DM to save 170k in agent size. Or: just include what we use
public class Activator extends DependencyActivatorBase implements AgentContext {

    private volatile ConfigurationHandler m_configurationHandler;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile DeploymentHandler m_deploymentHandler;
    private volatile DownloadHandler m_downloadHandler;
    private volatile ConnectionHandler m_connectionHandler;
    private volatile ScheduledExecutorService m_executorService;
    private volatile AgentControlImpl m_agentControl;
    private volatile AgentUpdateHandlerImpl m_agentUpdateHandler; // we use the implementation type here on purpose

    private volatile EventLoggerImpl m_eventLogger;
    private volatile DefaultController m_defaultController;

    private BundleContext m_bundleContext;
    private DependencyManager m_dependencyManager;
    private Component m_agentControlComponent;
    private Component m_eventLoggerComponent;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        m_bundleContext = context;
        m_dependencyManager = manager;

        m_executorService = Executors.newScheduledThreadPool(1);
        m_configurationHandler = new ConfigurationHandlerImpl(this);
        m_deploymentHandler = new DeploymentHandlerImpl(this);
        m_downloadHandler = new DownloadHandlerImpl(this);
        m_agentControl = new AgentControlImpl(this);
        m_agentUpdateHandler = new AgentUpdateHandlerImpl(this, context);

        Component service = createComponent().setImplementation(this)
            .setCallbacks("initAgent", "startAgent", "stopAgent", "destroyAgent")
            .setAutoConfig(BundleContext.class, false)
            .setAutoConfig(DependencyManager.class, false)
            .setAutoConfig(Component.class, false);

        if (Boolean.parseBoolean(System.getProperty("agent.identificationhandler.disabled"))) {
            service.add(createServiceDependency().setService(IdentificationHandler.class).setRequired(true));
        }
        else {
            m_identificationHandler = new IdentificationHandlerImpl(this);
        }

        if (Boolean.parseBoolean(System.getProperty("agent.discoveryhandler.disabled"))) {
            service.add(createServiceDependency().setService(DiscoveryHandler.class).setRequired(true));
        }
        else {
            m_discoveryHandler = new DiscoveryHandlerImpl(this);
        }

        if (Boolean.parseBoolean(System.getProperty("agent.connectionhandler.disabled"))) {
            service.add(createServiceDependency().setService(DiscoveryHandler.class).setRequired(true));
        }
        else {
            m_connectionHandler = new ConnectionHandlerImpl(this);
        }

        if (!Boolean.parseBoolean(System.getProperty("agent.defaultcontroller.disabled"))) {
            m_defaultController = new DefaultController(m_agentControl, m_executorService);
        }

        m_eventLogger = new EventLoggerImpl(m_agentControl, m_bundleContext);

        manager.add(service);
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    void startAgent() throws Exception {
        System.out.println("Starting agent!");

        m_agentControlComponent = createComponent()
            .setInterface(AgentControl.class.getName(), null)
            .setImplementation(m_agentControl);
        m_dependencyManager.add(m_agentControlComponent);

        m_eventLoggerComponent = createComponent()
            .setInterface(EventHandler.class.getName(), new Properties() {
                {
                    put(EventConstants.EVENT_TOPIC, EventLoggerImpl.TOPICS_INTEREST);
                }
            })
            .setImplementation(m_eventLogger);
        m_dependencyManager.add(m_eventLoggerComponent);
        m_bundleContext.addBundleListener(m_eventLogger);
        m_bundleContext.addFrameworkListener(m_eventLogger);

        if (m_defaultController != null) {
            m_defaultController.start();
        }
        // at this point we know the agent has started, so any updater bundle that
        // might still be running can be uninstalled
        m_agentUpdateHandler.uninstallUpdaterBundle();
    }

    void stopAgent() throws Exception {
        System.out.println("Stopping agent");
        if (m_defaultController != null) {
            m_defaultController.stop();
        }

        m_bundleContext.removeFrameworkListener(m_eventLogger);
        m_bundleContext.removeBundleListener(m_eventLogger);
        m_dependencyManager.remove(m_eventLoggerComponent);

        m_dependencyManager.remove(m_agentControlComponent);
    }

    @Override
    public IdentificationHandler getIdentificationHandler() {
        return m_identificationHandler;
    }

    @Override
    public DiscoveryHandler getDiscoveryHandler() {
        return m_discoveryHandler;
    }

    @Override
    public DeploymentHandler getDeploymentHandler() {
        return m_deploymentHandler;
    }

    @Override
    public ScheduledExecutorService getExecutorService() {
        return m_executorService;
    }

    @Override
    public ConfigurationHandler getConfigurationHandler() {
        return m_configurationHandler;
    }

    @Override
    public ConnectionHandler getConnectionHandler() {
        return m_connectionHandler;
    }

    @Override
    public DownloadHandler getDownloadHandler() {
        return m_downloadHandler;
    }

    @Override
    public AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentUpdateHandler;
    }

    @Override
    public File getWorkDir() {
        return m_bundleContext.getDataFile("");
    }
}
