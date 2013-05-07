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

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.ManagementAgent;
import org.apache.ace.agent.connection.ConnectionFactoryFactory;
import org.apache.ace.agent.deployment.DeploymentAdminDeployerFactory;
import org.apache.ace.agent.deployment.DeploymentCheckTaskFactory;
import org.apache.ace.agent.deployment.DeploymentServiceFactory;
import org.apache.ace.agent.deployment.DeploymentUpdateTaskFactory;
import org.apache.ace.agent.discovery.PropertyBasedDiscoveryFactory;
import org.apache.ace.agent.identification.IdentifierBasedIdentificationFactory;
import org.apache.ace.agent.logging.LogFactory;
import org.apache.ace.agent.logging.LogStoreFactory;
import org.apache.ace.agent.logging.LogSyncTaskFactory;
import org.apache.ace.agent.spi.ComponentFactory;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * Factory that handles configuration of management agents services. For every subsystem a {@link ComponentFactory} is
 * used to instantiate the actual components. Factories can be specified through configuration using a
 * <code>&lt;subsystem&gt;.factory</code> property.
 */
public class ManagementAgentFactory implements ManagedServiceFactory {

    private static final Set<String> DEFAULT_FACTORIES = new HashSet<String>();

    static {
        DEFAULT_FACTORIES.add(IdentifierBasedIdentificationFactory.class.getName());
        DEFAULT_FACTORIES.add(PropertyBasedDiscoveryFactory.class.getName());
        DEFAULT_FACTORIES.add(LogFactory.class.getName());
        DEFAULT_FACTORIES.add(LogStoreFactory.class.getName());
        DEFAULT_FACTORIES.add(LogSyncTaskFactory.class.getName());
        DEFAULT_FACTORIES.add(DeploymentServiceFactory.class.getName());
        DEFAULT_FACTORIES.add(DeploymentAdminDeployerFactory.class.getName());
        DEFAULT_FACTORIES.add(DeploymentCheckTaskFactory.class.getName());
        DEFAULT_FACTORIES.add(DeploymentUpdateTaskFactory.class.getName());
        DEFAULT_FACTORIES.add(ConnectionFactoryFactory.class.getName());
    }

    private final Map<String, Set<Component>> m_components = new HashMap<String, Set<Component>>();

    private volatile BundleContext m_context;
    private volatile DependencyManager m_manager;
    private volatile LogService m_logService;

    @Override
    public String getName() {
        return ManagementAgentFactory.class.getSimpleName();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void updated(String pid, Dictionary /* <string, String> */properties) throws ConfigurationException {

        Map<String, String> configuration = getConfigurationMap(properties);
        String agent = getAgentIdentifier(configuration);
        m_logService.log(LogService.LOG_DEBUG, "Receiving updated for pid/agent : " + pid + "/" + agent);

        Set<ComponentFactory> componentFactories = getComponentFactories(configuration);
        Set<Component> components = new HashSet<Component>();

        for (ComponentFactory componentFactory : componentFactories) {
            components.addAll(componentFactory.createComponents(m_context, m_manager, m_logService, configuration));
        }

        // This is kind of void but at present the only way for user-space consumers (like itest) to see that we
        // successfully configured the agent. Could be replaced by events. but this API may prove usefull in future to
        // expose limited functionality into user-space.
        Properties agentProperties = new Properties();
        agentProperties.put("agent", agent);
        Component agentComponent = m_manager.createComponent()
            .setInterface(ManagementAgent.class.getName(), agentProperties)
            .setImplementation(new ManagementAgent() {
            });
        components.add(agentComponent);

        synchronized (m_components) {
            m_components.put(pid, components);

        }

        for (Component component : components) {
            m_manager.add(component);
        }
    }

    @Override
    public void deleted(String pid) {
        Set<Component> deleted;
        synchronized (m_components) {
            deleted = m_components.remove(pid);
        }
        if (deleted != null) {
            for (Component component : deleted) {
                m_manager.remove(component);
            }
        }
    }

    private Set<ComponentFactory> getComponentFactories(Map<String, String> configuration) throws ConfigurationException {

        Set<ComponentFactory> componentFactories = new HashSet<ComponentFactory>();

        String factoriesProperty = ((String) configuration.get("factories"));
        if (factoriesProperty == null || factoriesProperty.equals("")) {
            for (String componentFactoryName : DEFAULT_FACTORIES) {
                ComponentFactory componentFactory = getComponentFactory(componentFactoryName);
                componentFactories.add(componentFactory);
            }
        }
        else {
            String[] componentFactoryNames = factoriesProperty.split(",");
            for (String componentFactoryName : componentFactoryNames) {
                ComponentFactory componentFactory = getComponentFactory(componentFactoryName.trim());
                componentFactories.add(componentFactory);
            }
        }
        return componentFactories;
    }

    /**
     * Converts the configuration dictionary to a Map<String, String>.
     */
    @SuppressWarnings("rawtypes")
    private Map<String, String> getConfigurationMap(Dictionary /* <String, String> */properties) {
        Map<String, String> configuration = new HashMap<String, String>();
        Enumeration/* <String> */enumeration = properties.keys();
        while (enumeration.hasMoreElements()) {
            Object key = enumeration.nextElement();
            configuration.put(key.toString(), properties.get(key).toString());
        }
        return configuration;
    }

    /**
     * Extracts the agent identifier form the configuration.
     */
    private String getAgentIdentifier(Map<String, String> configuration) throws ConfigurationException {
        String agentIdentifier = ((String) configuration.get("agent"));
        if (agentIdentifier != null) {
            agentIdentifier = agentIdentifier.trim();
        }
        if (agentIdentifier == null || agentIdentifier.equals("")) {
            throw new ConfigurationException("agent", "Updating an agent requires a valid configuration (empty name)");
        }
        return agentIdentifier;
    }

    /**
     * Returns a factory based on the specified FQN.
     */
    private ComponentFactory getComponentFactory(String componentFactoryName) throws ConfigurationException {

        try {
            Class<?> clazz = ManagementAgentFactory.class.getClassLoader().loadClass(componentFactoryName);
            if (!ComponentFactory.class.isAssignableFrom(clazz)) {
                throw new ConfigurationException("factories", "Factory class does not implement ComponentFactory interface: " + componentFactoryName);
            }
            try {
                Object instance = clazz.newInstance();
                return (ComponentFactory) instance;
            }
            catch (InstantiationException e) {
                throw new ConfigurationException("factories", "Factory class does not have a default constructor: " + componentFactoryName);
            }
            catch (IllegalAccessException e) {
                throw new ConfigurationException("factories", "Factory class does not have a default constructor: " + componentFactoryName);
            }
        }
        catch (ClassNotFoundException e) {
            throw new ConfigurationException("factories", "Factory class not found: " + componentFactoryName);
        }
    }
}
