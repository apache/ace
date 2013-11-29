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

import static org.apache.ace.agent.AgentConstants.CONFIG_KEY_NAMESPACE;
import static org.apache.ace.agent.AgentConstants.CONFIG_KEY_RETAIN;
import static org.apache.ace.agent.AgentConstants.EVENT_AGENT_CONFIG_CHANGED;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.ConfigurationHandler;

/**
 * Default thread-safe {@link ConfigurationHandler} implementation.
 */
public class ConfigurationHandlerImpl extends ComponentBase implements ConfigurationHandler, Runnable {
    /** Directory name use for storage. It is relative to the agent context work directory. */
    public static final String CONFIG_STORAGE_SUBDIR = "config";
    /** File name use for storage. */
    public static final String CONFIG_STORAGE_FILENAME = "config.properties";

    private ResettableTimer m_timer;
    private volatile ConcurrentMap<Object, Object> m_configProps;

    public ConfigurationHandlerImpl() {
        super("configuration");

        m_configProps = new ConcurrentHashMap<Object, Object>();
    }

    @Override
    public String get(String key, String defaultValue) {
        String value = (String) m_configProps.get(key);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, "");
        if (value.equals("")) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = get(key, "");
        try {
            if (!"".equals(value)) {
                return Integer.decode(value);
            }
        }
        catch (NumberFormatException exception) {
            // Ignore; return default...
        }
        return defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String value = get(key, "");
        try {
            if (!"".equals(value)) {
                return Long.decode(value);
            }
        }
        catch (NumberFormatException exception) {
            // Ignore; return default...
        }
        return defaultValue;
    }

    @Override
    public Set<String> keySet() {
        Set<String> keySet = new HashSet<String>();
        for (Object key : m_configProps.keySet()) {
            keySet.add((String) key);
        }
        return keySet;
    }

    @Override
    public void putAll(Map<String, String> props) {
        m_configProps.putAll(props);
        fireConfigChangeEventAsynchronously();
        // causes the config to be written eventually...
        scheduleStore();
    }

    /**
     * Called by {@link ResettableTimer} when a certain timeout has exceeded.
     */
    @Override
    public void run() {
        try {
            storeConfig();
        }
        catch (IOException exception) {
            logWarning("Storing configuration failed!", exception);
        }
    }

    @Override
    protected void onInit() throws Exception {
        loadConfig();
        loadSystemProps();

        m_timer = new ResettableTimer(getExecutorService(), this, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void onStart() throws Exception {
        // Notify all interested listeners about this, but do this the first time synchronously, see ACE-431.
        fireConfigChangeEventSynchronously();
    }

    @Override
    protected void onStop() throws Exception {
        // Make sure the configuration is written one last time...
        storeConfig();
    }

    private void fireConfigChangeEventAsynchronously() {
        getEventsHandler().postEvent(EVENT_AGENT_CONFIG_CHANGED, getConfigurationSnapshot());
    }

    private void fireConfigChangeEventSynchronously() {
        getEventsHandler().sendEvent(EVENT_AGENT_CONFIG_CHANGED, getConfigurationSnapshot());
    }

    private File getConfigDir() throws IOException {
        File dir = new File(getAgentContext().getWorkDir(), CONFIG_STORAGE_SUBDIR);
        if (!dir.exists() && !dir.mkdir()) {
            throw new IOException("Unable to acces configuration directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private File getConfigFile() throws IOException {
        File file = new File(getConfigDir(), CONFIG_STORAGE_FILENAME);
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Unable to acces configuration file: " + file.getAbsolutePath());
        }
        return file;
    }

    /**
     * @return a new map instance with a snapshot of the current configuration, never <code>null</code>.
     */
    private Map<String, String> getConfigurationSnapshot() {
        Map<String, String> props = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : m_configProps.entrySet()) {
            props.put((String) entry.getKey(), (String) entry.getValue());
        }
        return props;
    }

    private void loadConfig() throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(getConfigFile());

            Properties props = new Properties();
            props.load(input);

            m_configProps = new ConcurrentHashMap<Object, Object>(props);
        }
        finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private void loadSystemProps() {
        Properties sysProps = System.getProperties();

        for (Entry<Object, Object> entry : sysProps.entrySet()) {
            String key = (String) entry.getKey();

            if (key.startsWith(CONFIG_KEY_NAMESPACE) && !key.endsWith(CONFIG_KEY_RETAIN)) {
                boolean retain = Boolean.parseBoolean(sysProps.getProperty(key.concat(CONFIG_KEY_RETAIN)));

                if (!retain || !m_configProps.containsKey(key)) {
                    m_configProps.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void scheduleStore() {
        if (!m_timer.schedule()) {
            logWarning("Cannot schedule task to store configuration. Executor is shut down!");
        }
    }

    private void storeConfig() throws IOException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(getConfigFile());

            Properties props = new Properties();
            props.putAll(m_configProps);
            props.store(output, "ACE Agent configuration");
        }
        finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
