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
    private volatile AgentUpdateHandler m_agentUpdateHandler;

    private volatile DefaultController m_controller;

    private DependencyManager m_manager;
    private Component m_component;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        m_manager = manager;

        m_executorService = Executors.newScheduledThreadPool(1);
        m_configurationHandler = new ConfigurationHandlerImpl(this);
        m_deploymentHandler = new DeploymentHandlerImpl(this);
        m_downloadHandler = new DownloadHandlerImpl(this);
        m_agentControl = new AgentControlImpl(this);
        m_agentUpdateHandler = new AgentUpdateHandlerImpl(this, context);

        Component service = createComponent().setImplementation(this)
            .setCallbacks("initAgent", "startAgent", "stopAgent", "destroyAgent")
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
            m_controller = new DefaultController(m_agentControl, m_executorService);
        }
        
        manager.add(service);
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    void startAgent() throws Exception {
        System.out.println("Starting agent!");
        m_component = createComponent()
            .setInterface(AgentControl.class.getName(), null)
            .setImplementation(m_agentControl);
        m_manager.add(m_component);
        if (m_controller != null)
            m_controller.start();
    }

    void stopAgent() throws Exception {
        System.out.println("Stopping agent");
        if (m_controller != null)
            m_controller.stop();
        m_manager.remove(m_component);
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
}
