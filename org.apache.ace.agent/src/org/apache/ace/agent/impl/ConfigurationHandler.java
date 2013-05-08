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
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.Constants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * Handles management agent configuration system properties and subsequently calls
 * {@link AgentFactory#updated(String, Dictionary)} for every agent specific configuration.
 * 
 */
public class ConfigurationHandler implements ManagedService {

    private static final String DEFAULTS_RESOURCE = "org/apache/ace/agent/impl/agent-defaults.properties";

    private final Set<BundleActivator> m_startedActivators = new HashSet<BundleActivator>();
    private final Set<String> m_configuredAgentPIDs = new HashSet<String>();

    // injected services
    private volatile BundleContext m_context;
    private volatile ManagedServiceFactory m_agentFactory;
    private volatile LogService m_logService;

    private volatile Dictionary<String, String> m_defaultConfiguration;
    private volatile boolean m_verbose;

    // service life-cycle
    public void start() throws Exception {
        m_defaultConfiguration = loadDefaultConfiguration();
    }

    public void stop() throws Exception {
        stopConfiguredAgents();
        stopSystemActivators();
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    public void updated(Dictionary/* <String, String> */configuration) throws ConfigurationException {

        // CLI verbose support
        m_verbose = (configuration.get("verbose") != null) && Boolean.parseBoolean((String) configuration.get("verbose"));

        @SuppressWarnings("unchecked")
        Dictionary<String, String> mergedConfiguration = getMergedConfiguration((Dictionary<String, String>) configuration);

        stopConfiguredAgents();
        stopSystemActivators();

        startSystemActivators(mergedConfiguration);
        startConfiguredAgents(mergedConfiguration);
    }

    private Dictionary<String, String> loadDefaultConfiguration() throws IOException {
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
        Dictionary<String, String> configuration = new Hashtable<String, String>();
        for (Object key : properties.keySet()) {
            configuration.put((String) key, (String) properties.getProperty((String) key));
        }
        return configuration;
    }

    private void startConfiguredAgents(Dictionary<String, String> configuration) throws ConfigurationException {

        String agentsProperty = (String) configuration.get("agents");
        if (agentsProperty == null || agentsProperty.equals("")) {
            if (m_verbose)
                System.out.println("Configuration does not specify any agents");
            m_logService.log(LogService.LOG_WARNING, "Configuration does not specify any agents");
            return;
        }

        String[] agents = agentsProperty.split(",");
        for (String agent : agents) {
            Dictionary<String, String> dictionary = getAgentConfiguration(agent, configuration);
            String agentPID = "pid-agent-" + agent;
            if (m_verbose)
                System.out.println("Configuring new agent.. " + dictionary);
            synchronized (m_configuredAgentPIDs) {
                m_configuredAgentPIDs.add(agentPID);
            }
            m_agentFactory.updated(agentPID, dictionary);
        }
    }

    private void stopConfiguredAgents() {
        Set<String> configuredAgentPIDs = new HashSet<String>();
        synchronized (m_configuredAgentPIDs) {
            configuredAgentPIDs.addAll(m_configuredAgentPIDs);
            m_configuredAgentPIDs.clear();
        }
        for (String configuredAgentPID : configuredAgentPIDs) {
            if (m_verbose)
                System.out.println("Removing configured agent with PID " + configuredAgentPID);
            m_agentFactory.deleted(configuredAgentPID);
        }
    }

    private void startSystemActivators(Dictionary<String, String> configuration) throws ConfigurationException {

        Set<String> bundleActivators = null;
        String activatorsProperty = (String) configuration.get(Constants.CONFIG_ACTIVATORS_KEY);
        if (activatorsProperty != null && !activatorsProperty.equals("")) {
            bundleActivators = new HashSet<String>();
            String[] activators = activatorsProperty.split(",");
            for (String activator : activators) {
                bundleActivators.add(activator.trim());
            }
        }

        for (String bundleActivatorName : bundleActivators) {
            BundleActivator activator = getBundleActivator(bundleActivatorName);
            if (m_verbose)
                System.out.println("Starting system activator.. " + activator.getClass().getName());
            try {
                activator.start(m_context);
            }
            catch (Exception e) {
                if (m_verbose)
                    e.printStackTrace();
                m_logService.log(LogService.LOG_WARNING, "Activator stop exception! Continuing...", e);
            }
            synchronized (m_startedActivators) {
                m_startedActivators.add(activator);
            }
        }
    }

    private void stopSystemActivators() throws ConfigurationException {

        Set<BundleActivator> bundleActivators = new HashSet<BundleActivator>();
        synchronized (m_startedActivators) {
            bundleActivators.addAll(m_startedActivators);
            m_startedActivators.clear();
        }

        for (BundleActivator activator : bundleActivators) {
            if (m_verbose)
                System.out.println("Stopping system activator.. " + activator.getClass().getName());
            try {
                activator.stop(m_context);
            }
            catch (Exception e) {
                if (m_verbose)
                    e.printStackTrace();
                m_logService.log(LogService.LOG_WARNING, "Activator stop exception! Continuing...", e);
            }
        }
    }

    private Dictionary<String, String> getMergedConfiguration(Dictionary<String, String> configuration) {

        Dictionary<String, String> mergedConfiguration = new Hashtable<String, String>();
        Dictionary<String, String> defaultConfiguration = m_defaultConfiguration;

        // first map all default properties
        Enumeration<String> defaultKeys = defaultConfiguration.keys();
        while (defaultKeys.hasMoreElements()) {
            String key = defaultKeys.nextElement();
            mergedConfiguration.put(key, defaultConfiguration.get(key));
        }
        // overwrite with configuration properties
        Enumeration<String> configurationKeys = configuration.keys();
        while (configurationKeys.hasMoreElements()) {
            String key = configurationKeys.nextElement();
            mergedConfiguration.put(key, configuration.get(key));
        }
        return mergedConfiguration;
    }

    private Dictionary<String, String> getAgentConfiguration(String agent, Dictionary<String, String> configuration) throws ConfigurationException {

        String agentPrefix = agent + ".";
        Dictionary<String, String> agentConfiguration = new Hashtable<String, String>();

        // first map all global properties
        Enumeration<String> configurationKeys = configuration.keys();
        while (configurationKeys.hasMoreElements()) {
            String key = configurationKeys.nextElement();
            if (!key.startsWith(agentPrefix)) {
                agentConfiguration.put(key, configuration.get(key));
            }
        }

        // overwrite with agent specific properties
        configurationKeys = configuration.keys();
        while (configurationKeys.hasMoreElements()) {
            String key = configurationKeys.nextElement();
            if (key.startsWith(agentPrefix)) {
                agentConfiguration.put(key.replaceFirst(agentPrefix, ""), configuration.get(key));
            }
        }

        agentConfiguration.put("agent", agent);
        return agentConfiguration;
    }

    private BundleActivator getBundleActivator(String bundleActivatorName) throws ConfigurationException {

        try {
            Class<?> clazz = ConfigurationHandler.class.getClassLoader().loadClass(bundleActivatorName);
            if (!BundleActivator.class.isAssignableFrom(clazz)) {
                throw new ConfigurationException("activators", "Factory class does not implement ComponentFactory interface: " + bundleActivatorName);
            }
            try {
                Object instance = clazz.newInstance();
                return (BundleActivator) instance;
            }
            catch (InstantiationException e) {
                throw new ConfigurationException("activators", "BundleActivator class does not have a default constructor: " + bundleActivatorName);
            }
            catch (IllegalAccessException e) {
                throw new ConfigurationException("activators", "BundleActivator class does not have a default constructor: " + bundleActivatorName);
            }
        }
        catch (ClassNotFoundException e) {
            throw new ConfigurationException("activators", "BundleActivator class not found: " + bundleActivatorName);
        }
    }
}
