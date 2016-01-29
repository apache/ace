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
package org.apache.ace.configurator;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;

/**
 * Configures bundles managed by the <code>ConfigurationAdmin</code>. This Configurator uses text files as configuration
 * files containing properties. When a configuration file is added, the properties are being read and added. If the
 * config file is removed, the properties are removed as well.
 * <p>
 * The configuration files should be stored in the configuration directory (often the 'conf' directory) of the OSGi
 * framework and should have the format: &lt;pid&gt;.cfg
 * <p>
 * Note: this Configurator is based upon the principle in the FileInstall bundle Peter Kriens wrote. (see
 * http://www.aqute.biz/Code/FileInstall for more information)
 */
public class Configurator implements Runnable {
    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";

    private static final FileFilter FILENAME_FILTER = new FileFilter() {
        public boolean accept(File file) {
            return !file.isHidden() && (file.getName().endsWith(".cfg") || file.isDirectory());
        }
    };

    private static final String FACTORY_INSTANCE_KEY = "factory.instance.pid";

    private volatile LogService m_log; /* injected by dependency manager */
    private volatile ConfigurationAdmin m_configAdmin; /* injected by dependency manager */
    private volatile BundleContext m_context; /* injected by dependency manager */

    private final File m_configDir;
    private final long m_pollInterval;
    private final Map<String, Long> m_checksums = new HashMap<>();
    private final Map<String, Map<String, Long>> m_foundFactories = new HashMap<>();
    private final boolean m_reconfig;

    private Thread m_configThread;

    /**
     * Instantiates a new configurator.
     * 
     * @param dir
     *            The directory to watch.
     * @param pollInterval
     *            The poll iterval in ms.
     * @param reconfig
     *            Whether or not to use reconfiguration: if <code>false</code>, existing configuration values will not
     *            be overwritten, only new values (for a given pid) will be added.
     */
    public Configurator(File dir, long pollInterval, boolean reconfig) {
        if ((dir == null) || !dir.isDirectory() || (pollInterval < 0)) {
            throw new IllegalArgumentException("Bad arguments; either not an existing directory or an invalid interval.");
        }
        m_configDir = dir;
        m_pollInterval = pollInterval;
        m_reconfig = reconfig;
    }

    /**
     * Starts the Configuration timer.
     */
    synchronized void start() {
        if (m_configThread == null) {
            m_configThread = new Thread(this, "Apache ACE Configurator");
        }
        m_configThread.setDaemon(true);
        m_configThread.start();
    }

    /**
     * Stops the Configuration timer.
     * 
     * @throws InterruptedException
     */
    synchronized void stop() throws InterruptedException {
        // Join in stop to prevent race condition, careful with bundle location setting to null
        m_configThread.interrupt();
        m_configThread.join();
        m_configThread = null;
        m_checksums.clear();
    }

    /**
     * Starts the actual Timer task, and calls the configurator to make sure the configurations are performed. Checking
     * whether a new configuration is present, will be done with an interval that can be defined via a system property.
     */
    public void run() {
        try {
            while (!Thread.interrupted()) {
                doConfigs();
                Thread.sleep(m_pollInterval);
            }
        }
        catch (InterruptedException ex) {
            // We are requested to stop.
        }
    }

    /**
     * Enables the actual configuring of OSGi ManagedServices. It makes sure all new configurations are added, changed
     * configurations are updated, and old configurations are removed. Configurations are updated when the timestamp or
     * the size of the new configuration has changed.
     */
    private void doConfigs() {
        Set<String> pids = new HashSet<>(m_checksums.keySet());

        File[] files = m_configDir.listFiles(FILENAME_FILTER);
        for (int i = 0; (files != null) && (i < files.length); i++) {
            File file = files[i];
            String pid = parsePid(file);

            if (file.isDirectory()) {
                doFactoryConfigs(pid, file.listFiles(FILENAME_FILTER));
            }
            else {
                Long newChecksum = new Long(file.lastModified() ^ file.length());
                Long oldChecksum = (Long) m_checksums.get(pid); // may be null, intended
                if (!newChecksum.equals(oldChecksum)) {
                    m_checksums.put(pid, newChecksum);
                    processConfigFile(file, null);
                }
                pids.remove(pid);
            }
        }
        for (String pid : pids) {
            deleteConfig(pid, null);
            m_checksums.remove(pid);
        }
    }

    private void doFactoryConfigs(String factoryPid, File[] newInstances) {
        if (!m_foundFactories.containsKey(factoryPid)) {
            m_foundFactories.put(factoryPid, new HashMap<String, Long>());
        }
        Map<String, Long> instances = m_foundFactories.get(factoryPid);
        Set<String> instancesPids = new HashSet<>(instances.keySet());

        for (int j = 0; j < newInstances.length; j++) {
            File instanceConfigFile = newInstances[j];
            String instancePid = parsePid(instanceConfigFile);

            Long newChecksum = new Long(instanceConfigFile.lastModified() ^ instanceConfigFile.length());
            Long oldChecksum = (Long) instances.get(instancePid);
            if (!newChecksum.equals(oldChecksum)) {
                instances.put(instancePid, newChecksum);
                processConfigFile(instanceConfigFile, factoryPid);
            }
            instancesPids.remove(instancePid);
        }

        for (String instancePid : instancesPids) {
            deleteConfig(instancePid, factoryPid);
            instances.remove(instancePid);
        }
    }

    /**
     * Sets the Configuration and calls update() to do the actual configuration on the ManagedService. If and only if
     * the configuration did not exist before or has changed. A configuration has changed if the length or the
     * lastModified date has changed.
     */
    private void processConfigFile(File configFile, String factoryPid) {
        try (InputStream in  = new FileInputStream(configFile)) {
            Properties properties = new Properties();
            properties.load(in);

            String pid = parsePid(configFile);
            properties = substVars(properties);
            configure(pid, factoryPid, properties);
        }
        catch (IOException ex) {
            m_log.log(LogService.LOG_ERROR, "Unable to read configuration from file: " + configFile.getAbsolutePath(), ex);
        }
    }

    private void configure(String pid, String factoryPid, Properties properties) {
        try {
            Configuration config = getConfiguration(pid, factoryPid);

            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }

            List<String> curKeys = Collections.list(props.keys());
            for (Object key : properties.keySet()) {
                if (curKeys.contains(key) && !m_reconfig) {
                    m_log.log(LogService.LOG_DEBUG, "Using previously configured value for bundle=" + pid + " key=" + key);
                } else {
                    props.put((String) key, properties.get(key));
                }
            }

            if (factoryPid != null) {
                props.put(FACTORY_INSTANCE_KEY, factoryPid + "_" + pid);
            }

            config.update(props);
            m_log.log(LogService.LOG_DEBUG, "Updated configuration for pid '" + pid + "' (" + props + ")");
        }
        catch (IOException ex) {
            m_log.log(LogService.LOG_ERROR, "Unable to update configuration for pid '" + pid + "'", ex);
        }
    }

    private Configuration getConfiguration(String pid, String factoryPid) throws IOException {
        if (factoryPid != null) {
            Configuration[] configs = null;
            try {
                configs = m_configAdmin.listConfigurations("(" + FACTORY_INSTANCE_KEY + "=" + factoryPid + "_" + pid + ")");
            }
            catch (InvalidSyntaxException e) {
                m_log.log(LogService.LOG_ERROR, "Exception during lookup of configuration of managed service factory instance '" + pid + "'", e);
            }
            if ((configs == null) || (configs.length == 0)) {
                return m_configAdmin.createFactoryConfiguration(factoryPid, null);
            }
            else {
                return configs[0];
            }
        }
        else {
            return m_configAdmin.getConfiguration(pid, null);
        }
    }

    /**
     * Removes a configuration from ConfigAdmin.
     */
    protected void deleteConfig(String pid, String factoryPid) {
        try {
            Configuration config = getConfiguration(pid, factoryPid);
            config.delete();
            m_log.log(LogService.LOG_DEBUG, "Removed configuration for pid '" + pid + "'");
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Unable to remove configuration for pid '" + pid + "'", e);
        }
    }

    /**
     * Remove the config extension (.cfg) and return the resulting String.
     */
    protected String parsePid(File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            // factory pid
            return name;
        }
        else {
            return name.substring(0, name.length() - 4);
        }
    }

    /**
     * Performs variable substitution for a complete set of properties
     * 
     * @see #substVars(String, String, java.util.Map, java.util.Properties)
     * @param properties
     *            Set of properties to apply substitution on.
     * @return Same set of properties with all variables substituted.
     */
    private Properties substVars(Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = (String) entry.getKey();
            String value = (String) entry.getValue();
            properties.setProperty(name, substVars(value, name, null, properties));
        }
        return properties;
    }

    /**
     * <p>
     * This method performs property variable substitution on the specified value. If the specified value contains the
     * syntax <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt> refers to either a configuration property
     * or a system property, then the corresponding property value is substituted for the variable placeholder. Multiple
     * variable placeholders may exist in the specified value as well as nested variable placeholders, which are
     * substituted from inner most to outer most. Configuration properties override system properties.
     * </p>
     * 
     * @param val
     *            The string on which to perform property substitution.
     * @param currentKey
     *            The key of the property being evaluated used to detect cycles.
     * @param cycleMap
     *            Map of variable references used to detect nested cycles.
     * @param configProps
     *            Set of configuration properties.
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException
     *             If there was a syntax error in the property placeholder syntax or a recursive variable reference.
     */
    private String substVars(String val, String currentKey, Map<String, String> cycleMap, Properties configProps) throws IllegalArgumentException {
        // If there is currently no cycle map, then create
        // one for detecting cycles for this invocation.
        if (cycleMap == null) {
            cycleMap = new HashMap<>();
        }

        // Put the current key in the cycle map.
        cycleMap.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int stopDelim = val.indexOf(DELIM_STOP);

        // Find the matching starting "${" variable delimiter
        // by looping until we find a start delimiter that is
        // greater than the stop delimiter we have found.
        int startDelim = val.indexOf(DELIM_START);
        while (stopDelim >= 0) {
            int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
            if ((idx < 0) || (idx > stopDelim)) {
                break;
            }
            else if (idx < stopDelim) {
                startDelim = idx;
            }
        }

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        // ACE-401: be liberal to the content, and do not throw an exception in case we see something that resembles a substitution but is in fact nothing...
        if ((startDelim < 0) || (stopDelim < 0) || (startDelim > stopDelim)) {
            return val;
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

        // Verify that this is not a recursive variable reference.
        if (cycleMap.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        String substValue = (configProps != null) ? configProps.getProperty(variable, null) : null;
        if (substValue == null) {
            // Ignore unknown property values, check whether it is defined as framework or system property...
            substValue = m_context.getProperty(variable);
            if (substValue == null) {
                // Still not found, then ignore this substitution...
                return val;
            }
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycleMap.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length(), val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = substVars(val, currentKey, cycleMap, configProps);

        // Return the value.
        return val;
    }
}
