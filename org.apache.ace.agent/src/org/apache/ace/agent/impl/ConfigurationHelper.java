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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.ace.agent.Constants;
import org.apache.ace.agent.spi.ComponentFactory;
import org.osgi.framework.BundleActivator;

/**
 * Handles management agent configuration system properties and subsequently calls
 * {@link ManagementAgentFactoryImpl#updated(String, Dictionary)} for every agent specific configuration.
 * 
 */
public class ConfigurationHelper {

    private static final String DEFAULTS_RESOURCE = "org/apache/ace/agent/impl/agent-defaults.properties";
    private final Map<String, String> m_configuration;
    private final boolean m_verbose;

    private final BundleActivator[] m_bundleActivators;
    private final String[] m_agentIds;
    private final Map<String, Map<String, String>> m_agentConfigurations;
    private final Map<String, ComponentFactory[]> m_agentComponentFactories;

    public ConfigurationHelper(Map<String, String> configuration) throws Exception {

        m_configuration = loadDefaultConfiguration();
        m_configuration.putAll(configuration);
        m_verbose = configuration.get("verbose") != null && Boolean.parseBoolean((String) configuration.get("verbose"));

        // eager loading for failfast
        m_agentIds = loadAgentIds();
        m_bundleActivators = loadBundleActivators();
        m_agentConfigurations = new HashMap<String, Map<String, String>>();
        m_agentComponentFactories = new HashMap<String, ComponentFactory[]>();
        for (String agentId : m_agentIds) {
            m_agentConfigurations.put(agentId, loadAgentConfiguration(agentId));
            m_agentComponentFactories.put(agentId, loadComponentFactories(agentId));
        }
    }

    public boolean isVerbose() {
        return m_verbose;
    }

    public String[] getAgentIds() {
        return m_agentIds;
    }

    public BundleActivator[] getBundleActivators() throws Exception {
        return m_bundleActivators;
    }

    public ComponentFactory[] getComponentFactories(String agentId) throws Exception {
        return m_agentComponentFactories.get(agentId);
    }

    public Map<String, String> getAgentConfiguration(String agentId) throws Exception {
        return m_agentConfigurations.get(agentId);
    }

    private String[] loadAgentIds() {
        List<String> agentList = getStringListProperty(m_configuration, Constants.CONFIG_AGENTS_KEY);
        return agentList.toArray(new String[agentList.size()]);
    }

    private BundleActivator[] loadBundleActivators() throws Exception {
        List<String> bundleActivatorList = getStringListProperty(m_configuration, Constants.CONFIG_ACTIVATORS_KEY);
        BundleActivator[] bundleActivators = new BundleActivator[bundleActivatorList.size()];
        int i = 0;
        for (String bundleActivatorName : bundleActivatorList) {
            bundleActivators[i++] = loadBundleActivator(bundleActivatorName);
        }
        return bundleActivators;
    }

    private ComponentFactory[] loadComponentFactories(String agentId) throws Exception {
        Map<String, String> agentConfiguration = getAgentConfiguration(agentId);
        List<String> componentFactryList = getStringListProperty(agentConfiguration, Constants.CONFIG_FACTORIES_KEY);
        ComponentFactory[] componentFactories = new ComponentFactory[componentFactryList.size()];
        int i = 0;
        for (String componentFactory : componentFactryList) {
            componentFactories[i++] = loadComponentFactory(componentFactory);
        }
        return componentFactories;
    }

    private Map<String, String> loadAgentConfiguration(String agentId) throws Exception {

        String agentPrefix = agentId + ".";
        Map<String, String> agentConfiguration = new HashMap<String, String>();
        // first map all global properties
        for (Entry<String, String> entry : m_configuration.entrySet()) {
            if (!entry.getKey().startsWith(agentPrefix)) {
                agentConfiguration.put(entry.getKey(), entry.getValue());
            }
        }
        // overwrite with agent specific properties
        for (Entry<String, String> entry : m_configuration.entrySet()) {
            if (entry.getKey().startsWith(agentPrefix)) {
                agentConfiguration.put(entry.getKey().replaceFirst(agentPrefix, ""), entry.getValue());
            }
        }
        agentConfiguration.put("agent", agentId);
        return agentConfiguration;
    }

    private List<String> getStringListProperty(Map<String, String> configuration, String key) {
        List<String> values = new ArrayList<String>();
        String value = (String) m_configuration.get(key);
        if (value != null && !value.equals("")) {
            String[] parts = value.split(",");
            for (String part : parts) {
                values.add(part.trim());
            }
        }
        return values;
    }

    private Map<String, String> loadDefaultConfiguration() throws IOException {
        Properties properties = new Properties();
        ClassLoader classloader = getClass().getClassLoader();
        InputStream inStream = classloader.getResourceAsStream(DEFAULTS_RESOURCE);
        if (inStream != null) {
            try {
                properties.load(inStream);
            }
            finally {
                inStream.close();
            }
        }
        Map<String, String> configuration = new HashMap<String, String>();
        for (Object key : properties.keySet()) {
            configuration.put((String) key, (String) properties.getProperty((String) key));
        }
        return configuration;
    }

    private BundleActivator loadBundleActivator(String bundleActivatorName) throws Exception {
        try {
            Class<?> clazz = ConfigurationHelper.class.getClassLoader().loadClass(bundleActivatorName);
            if (!BundleActivator.class.isAssignableFrom(clazz)) {
                throw new Exception("Factory class does not implement ComponentFactory interface: " + bundleActivatorName);
            }
            try {
                Object instance = clazz.newInstance();
                return (BundleActivator) instance;
            }
            catch (InstantiationException e) {
                throw new Exception("BundleActivator class does not have a default constructor: " + bundleActivatorName);
            }
            catch (IllegalAccessException e) {
                throw new Exception("BundleActivator class does not have a default constructor: " + bundleActivatorName);
            }
        }
        catch (ClassNotFoundException e) {
            throw new Exception("BundleActivator class not found: " + bundleActivatorName);
        }
    }

    private ComponentFactory loadComponentFactory(String componentFactoryName) throws Exception {
        try {
            Class<?> clazz = ManagementAgentFactoryImpl.class.getClassLoader().loadClass(componentFactoryName);
            if (!ComponentFactory.class.isAssignableFrom(clazz)) {
                throw new Exception("Factory class does not implement ComponentFactory interface: " + componentFactoryName);
            }
            try {
                Object instance = clazz.newInstance();
                return (ComponentFactory) instance;
            }
            catch (InstantiationException e) {
                throw new Exception("Factory class does not have a default constructor: " + componentFactoryName);
            }
            catch (IllegalAccessException e) {
                throw new Exception("Factory class does not have a default constructor: " + componentFactoryName);
            }
        }
        catch (Exception e) {
            throw new Exception("Factory class not found: " + componentFactoryName);
        }
    }

}
