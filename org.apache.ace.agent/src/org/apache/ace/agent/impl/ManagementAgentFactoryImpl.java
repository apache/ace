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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ace.agent.ManagementAgent;
import org.apache.ace.agent.ManagementAgentFactory;
import org.apache.ace.agent.spi.ComponentFactory;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Factory that handles configuration of management agents services. For every subsystem a {@link ComponentFactory} is
 * used to instantiate the actual components. Factories can be specified through configuration using a
 * <code>&lt;subsystem&gt;.factory</code> property.
 */
public class ManagementAgentFactoryImpl implements ManagementAgentFactory {

    private final Set<BundleActivator> m_startedActivators = new HashSet<BundleActivator>();
    private final Map<String, Set<Component>> m_agentComponents = new HashMap<String, Set<Component>>();

    // guards all state against concurrent updates without locking
    private final AtomicBoolean m_updating = new AtomicBoolean(false);

    private volatile BundleContext m_context;
    private volatile DependencyManager m_manager;
    private volatile LogService m_logService;
    private volatile boolean m_verbose;

    @Override
    public void updated(Map<String, String> configuration) throws Exception {

        if (!m_updating.compareAndSet(false, true)) {
            m_logService.log(LogService.LOG_WARNING, "Receiving updated while updating! Ignoring...");
            return;
        }
        try {

            ConfigurationHelper configurationHelper = new ConfigurationHelper(configuration);
            m_verbose = configurationHelper.isVerbose();
            m_logService.log(LogService.LOG_DEBUG, "Receiving updated configuration");
            if (m_verbose) {
                System.out.println("Receiving updated configuration");
            }

            removeAgents();
            stopBundleActivators();
            startBundleActivators(configurationHelper);
            createAgents(configurationHelper);
        }
        catch (Exception e) {
            m_logService.log(LogService.LOG_ERROR, "Agent update failed! ", e);
        }
        finally {
            m_updating.set(false);
        }
    }

    private void createAgents(ConfigurationHelper configurationHelper) throws Exception {
        for (String agentId : configurationHelper.getAgentIds()) {
            Map<String, String> agentConfiguration = configurationHelper.getAgentConfiguration(agentId);
            ComponentFactory[] componentFactories = configurationHelper.getComponentFactories(agentId);
            createAgent(agentId, componentFactories, agentConfiguration);
        }
    }

    private void createAgent(String agentId, ComponentFactory[] componentFactories, Map<String, String> agentConfiguration) throws Exception {
        try {
            Set<Component> components = new HashSet<Component>();
            for (ComponentFactory componentFactory : componentFactories) {
                components.addAll(componentFactory.createComponents(m_context, m_manager, m_logService, agentConfiguration));
            }

            Properties agentProperties = new Properties();
            agentProperties.put("agent", agentId);
            Component agentComponent = m_manager.createComponent()
                .setInterface(ManagementAgent.class.getName(), agentProperties)
                .setImplementation(new ManagementAgent() {
                });
            components.add(agentComponent);

            m_agentComponents.put(agentId, components);
            for (Component component : components) {
                m_manager.add(component);
            }
        }
        catch (Exception e) {
            if (m_verbose) {
                System.err.println("Failed to create agent component!");
                e.printStackTrace();
            }
            m_logService.log(LogService.LOG_ERROR, "Failed to create agent component: " + e.getMessage(), e);
            throw e;
        }
    }

    private void removeAgents() {
        for (Entry<String, Set<Component>> entry : m_agentComponents.entrySet()) {
            for (Component component : entry.getValue()) {
                try {
                    System.err.println("Removing " + entry.getKey() + " " + component);
                    m_manager.remove(component);
                }
                catch (Exception e) {
                    if (m_verbose) {
                        System.err.println("Failed to remove agent component!");
                        e.printStackTrace();
                    }
                    m_logService.log(LogService.LOG_ERROR, "Failed to remove agent component: " + e.getMessage(), e);
                }
            }
        }
        m_agentComponents.clear();
    }

    private void startBundleActivators(ConfigurationHelper configurationHelper) throws Exception {
        System.out.println("Starting system activators.. ");
        for (BundleActivator bundleActivator : configurationHelper.getBundleActivators()) {
            System.out.println("Starting system activator.. " + bundleActivator.getClass().getName());
            if (m_verbose) {
                System.out.println("Starting system activator.. " + bundleActivator.getClass().getName());
            }
            try {
                bundleActivator.start(m_context);
                m_startedActivators.add(bundleActivator);
            }
            catch (Exception e) {
                if (m_verbose) {
                    System.err.println("Activator start exception!");
                    e.printStackTrace();
                }
                m_logService.log(LogService.LOG_ERROR, "Activator stop exception!", e);
                throw e;
            }
        }
        System.out.println("Started system activators.. ");
    }

    private void stopBundleActivators() throws Exception {
        for (BundleActivator activator : m_startedActivators) {
            if (m_verbose)
                System.out.println("Stopping system activator.. " + activator.getClass().getName());
            try {
                activator.stop(m_context);
            }
            catch (Exception e) {
                if (m_verbose) {
                    System.err.println("Activator stop exception!");
                    e.printStackTrace();
                }
                m_logService.log(LogService.LOG_ERROR, "Activator stop exception!", e);
            }
            m_startedActivators.clear();
        }
    }
}
