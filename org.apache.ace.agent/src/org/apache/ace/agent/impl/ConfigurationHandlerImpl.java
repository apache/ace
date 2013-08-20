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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.ConfigurationHandler;
import org.osgi.service.log.LogService;

public class ConfigurationHandlerImpl implements ConfigurationHandler {

    private final AgentContext m_agentContext;
    private Properties m_configProps = null;

    public ConfigurationHandlerImpl(AgentContext agentContext) {
        m_agentContext = agentContext;
    }

    public void start() throws Exception {
        synchronized (this) {
            loadSystemProps();
        }
    }

    @Override
    public Set<String> keySet() {
        Set<String> keySet = new HashSet<String>();
        synchronized (this) {
            ensureLoadConfig();
            for (Object key : m_configProps.keySet()) {
                keySet.add((String) key);
            }
        }
        return Collections.unmodifiableSet(keySet);
    }

    @Override
    public void put(String key, String value) {
        ensureNotEmpty(key);
        ensureNotEmpty(value);
        synchronized (this) {
            ensureLoadConfig();
            String previous = (String) m_configProps.put(key, value);
            if (previous == null || !previous.equals(value)) {
                ensureStoreConfig();
            }
        }
    }

    @Override
    public void remove(String key) {
        ensureNotEmpty(key);
        synchronized (this) {
            ensureLoadConfig();
            Object value = m_configProps.remove(key);
            if (value != null) {
                ensureStoreConfig();
            }
        }
    }

    @Override
    public String get(String key, String defaultValue) {
        ensureNotEmpty(key);
        synchronized (this) {
            ensureLoadConfig();
            String value = (String) m_configProps.get(key);
            if (value == null) {
                value = defaultValue;
            }
            return value;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String value = get(key, "");
        if (value.equals("")) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    @Override
    public void putLong(String key, long value) {
        put(key, String.valueOf(value));
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
    public void putBoolean(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    private void loadSystemProps() {
        ensureLoadConfig();
        for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith(CONFIG_KEY_NAMESPACE) && !key.endsWith(CONFIG_KEY_OVERRIDEPOSTFIX)) {
                if (!m_configProps.containsKey(key)
                    || Boolean.parseBoolean(System.getProperties().getProperty(key + CONFIG_KEY_OVERRIDEPOSTFIX))) {
                    m_configProps.put(entry.getKey(), entry.getValue());
                }
            }
        }
        ensureStoreConfig();
    }

    private void ensureLoadConfig() {
        if (m_configProps != null) {
            return;
        }
        try {
            m_configProps = new Properties();
            loadConfig();
        }
        catch (IOException e) {
            m_agentContext.getLogService().log(LogService.LOG_ERROR, "Load config failed", e);
            throw new IllegalStateException("Load config failed", e);
        }
    }

    private void ensureStoreConfig() {
        if (m_configProps == null) {
            return;
        }
        try {
            storeConfig();
        }
        catch (IOException e) {
            m_agentContext.getLogService().log(LogService.LOG_ERROR, "Storing config failed", e);
            throw new IllegalStateException("Store config failed", e);
        }
    }

    private void loadConfig() throws IOException {
        File configFile = getConfigFile();
        InputStream input = new FileInputStream(configFile);
        try {
            m_configProps.clear();
            m_configProps.load(input);
        }
        finally {
            input.close();
        }
    }

    private void storeConfig() throws IOException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(getConfigFile());
            m_configProps.store(output, "ACE Agent configuration");
        }
        finally {
            output.close();
        }
    }

    private File getConfigFile() throws IOException {
        File file = new File(getConfigDir(), "config.properties");
        if (!file.exists() && !file.createNewFile())
            throw new IOException("Unable to acces configuration file: " + file.getAbsolutePath());
        return file;
    }

    private File getConfigDir() throws IOException {
        File dir = new File(m_agentContext.getWorkDir(), "config");
        if (!dir.exists() && !dir.mkdir())
            throw new IOException("Unable to acces configuration directory: " + dir.getAbsolutePath());
        return dir;
    }

    private static void ensureNotEmpty(String value) {
        if (value == null || value.equals(""))
            throw new IllegalArgumentException("Can not pass null as an argument");
    }

}
