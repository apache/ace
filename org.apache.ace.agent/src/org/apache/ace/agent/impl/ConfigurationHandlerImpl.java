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

import static org.apache.ace.agent.AgentConstants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.ConfigurationHandler;
import org.osgi.framework.BundleContext;

/**
 * Default thread-safe {@link ConfigurationHandler} implementation.
 */
public class ConfigurationHandlerImpl extends ComponentBase implements ConfigurationHandler, Runnable {
    /** Directory name use for storage. It is relative to the agent context work directory. */
    public static final String CONFIG_STORAGE_SUBDIR = "config";
    /** File name use for storage. */
    public static final String CONFIG_STORAGE_FILENAME = "config.properties";
    private final BundleContext m_context;
    private ResettableTimer m_timer;
    private volatile ConcurrentMap<Object, Object> m_configProps;

    public ConfigurationHandlerImpl(BundleContext context) {
        super("configuration");
        m_context = context;
        m_configProps = new ConcurrentHashMap<>();
    }

    @Override
    public String get(String key, String defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            value = defaultValue;
        }
        return String.valueOf(value);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return (value instanceof Boolean) ? ((Boolean) value).booleanValue() : Boolean.parseBoolean(String.valueOf(value));
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            else if (value instanceof String) {
                return Integer.decode((String) value);
            }
        }
        catch (NumberFormatException exception) {
            // Ignore; return default...
        }
        return defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            else if (value instanceof String) {
                return Long.decode((String) value);
            }
        }
        catch (NumberFormatException exception) {
            // Ignore; return default...
        }
        return defaultValue;
    }

    @Override
    public Set<String> keySet() {
        Set<String> keySet = new HashSet<>();
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
            throw new IOException("Unable to access configuration directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private File getConfigFile() throws IOException {
        File file = new File(getConfigDir(), CONFIG_STORAGE_FILENAME);
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Unable to access configuration file: " + file.getAbsolutePath());
        }
        return file;
    }

    /**
     * @return a new map instance with a snapshot of the current configuration, including the current (agent-specific)
     *         system properties, never <code>null</code>.
     */
    private Map<String, String> getConfigurationSnapshot() {
        Map<String, String> props = new HashMap<>();

        // First copy all agent-related system properties, as they can be overridden by local configuration options...
        Properties sysProps = System.getProperties();
        for (Map.Entry<Object, Object> entry : sysProps.entrySet()) {
            String key = (String) entry.getKey();

            if (key.startsWith(CONFIG_KEY_NAMESPACE)) {
                props.put(key, (String) entry.getValue());
            }
        }

        for (Map.Entry<Object, Object> entry : m_configProps.entrySet()) {
            props.put((String) entry.getKey(), (String) entry.getValue());
        }

        return props;
    }

    private Object getProperty(String key) {
        Object result = m_configProps.get(key);
        if (result == null) {
            result = m_context.getProperty(key);
        }
        return result;
    }

    private void loadConfig() throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(getConfigFile());

            Properties props = new Properties();
            props.load(input);

            m_configProps.putAll(props);
        }
        finally {
            if (input != null) {
                input.close();
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
