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

import static org.apache.ace.agent.Constants.FACTORY_PID;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * Configuration component that reads a management agent properties file and calls
 * {@link ManagementAgentFactory#updated(String, Dictionary)} for every agent configuration.
 * 
 */
@SuppressWarnings("restriction")
public class StaticConfigurationHandler {

    private static final Set<String> DEFAULT_ACTIVATORS = new HashSet<String>();

    static {
        DEFAULT_ACTIVATORS.add(org.apache.ace.connectionfactory.impl.Activator.class.getName());
        DEFAULT_ACTIVATORS.add(org.apache.ace.scheduler.Activator.class.getName());
        DEFAULT_ACTIVATORS.add(org.apache.felix.deploymentadmin.Activator.class.getName());
    }

    private final Set<BundleActivator> m_activators = new HashSet<BundleActivator>();

    // injected services
    private volatile BundleContext m_context;
    private volatile ManagedServiceFactory m_agentFactory;
    private volatile LogService m_logService;

    // lifecycle callback
    public void start() throws Exception {
        loadStaticConfiguration();
    }

    // lifecycle callback
    public void stop() throws Exception {
        for (BundleActivator activator : m_activators) {
            try {
                activator.stop(m_context);
            }
            catch (Exception e) {
                m_logService.log(LogService.LOG_WARNING, "Activator stop exception", e);
            }
        }
        m_activators.clear();
    }

    /**
     * Load static configuration that may hold multiple agents.
     * 
     */
    @SuppressWarnings({ "rawtypes" })
    private void loadStaticConfiguration() throws Exception {

        Dictionary configuration = null;

        String staticConfigurationFile = System.getProperty(FACTORY_PID);
        if (staticConfigurationFile != null) {
            configuration = loadProperties(new File(staticConfigurationFile));
        }
        if (configuration == null) {
            configuration = new Hashtable();
        }

        startActivators(configuration);
        configureAgents(configuration);
    }

    private void configureAgents(@SuppressWarnings("rawtypes") Dictionary configuration) throws Exception {

        String agentsProperty = (String) configuration.get("agents");
        if (agentsProperty == null || agentsProperty.equals("")) {
            m_logService.log(LogService.LOG_WARNING, "Configuration does not specify any agents");
            return;
        }

        String[] agents = agentsProperty.split(",");
        for (String agent : agents) {
            @SuppressWarnings("rawtypes")
            Dictionary dictionary = getAgentConfiguration(agent, configuration);
            m_agentFactory.updated("static-" + agent, dictionary);
        }
    }

    private void startActivators(@SuppressWarnings("rawtypes") Dictionary configuration) throws Exception {

        Set<String> bundleActivators = null;
        String activatorsProperty = (String) configuration.get("activators");
        if (activatorsProperty == null || activatorsProperty.equals("")) {
            bundleActivators = DEFAULT_ACTIVATORS;
        }
        else {
            bundleActivators = new HashSet<String>();
            String[] activators = activatorsProperty.split(",");
            for (String activator : activators) {
                bundleActivators.add(activator.trim());
            }
        }

        for (String bundleActivatorName : bundleActivators) {
            BundleActivator activator = getBundleActivator(bundleActivatorName);
            activator.start(m_context);
            m_activators.add(activator);
        }
    }

    /**
     * Extract an agent specific configuration.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Dictionary getAgentConfiguration(String agent, Dictionary configuration) throws Exception {
        String agentPrefix = agent + ".";
        Dictionary dictionary = new Hashtable();

        // first map all global properties
        Enumeration/* <String> */keys = configuration.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if (!key.startsWith(agentPrefix)) {
                dictionary.put(key, configuration.get(key));
            }
        }

        // overwrite with agent specific properties
        keys = configuration.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if (key.startsWith(agentPrefix)) {
                dictionary.put(key.replaceFirst(agentPrefix, ""), configuration.get(key));
            }
        }

        dictionary.put("agent", agent);
        return dictionary;
    }

    /**
     * Load the properties file from disk.
     */
    @SuppressWarnings({ "rawtypes" })
    private Dictionary loadProperties(File configurationFile) throws Exception {
        if (!configurationFile.exists()) {
            m_logService.log(LogService.LOG_WARNING, "Specified configuration file does not exist: " + configurationFile.getAbsolutePath());
            return null;
        }
        if (!configurationFile.isFile()) {
            m_logService.log(LogService.LOG_WARNING, "Specified configuration file is not a regular file: " + configurationFile.getAbsolutePath());
            return null;
        }
        if (!configurationFile.canRead()) {
            m_logService.log(LogService.LOG_WARNING, "Specified configuration file can not be read: " + configurationFile.getAbsolutePath());
            return null;
        }
        Properties configurationProperties = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(configurationFile);
            configurationProperties.load(fis);
            return configurationProperties;
        }
        catch (IOException e) {
            m_logService.log(LogService.LOG_WARNING, "Specified configuration file is invalid: " + configurationFile.getAbsolutePath(), e);
            return null;
        }
        finally {
            try {
                if (fis != null)
                    fis.close();
            }
            catch (IOException e) {
            }
        }
    }

    /**
     * Returns a bundle activator based on the specified FQN.
     */
    private BundleActivator getBundleActivator(String bundleActivatorName) throws ConfigurationException {

        try {
            Class<?> clazz = StaticConfigurationHandler.class.getClassLoader().loadClass(bundleActivatorName);
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
