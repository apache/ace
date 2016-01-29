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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public abstract class MockConfigAdmin implements ConfigurationAdmin {
    private final Map<String, Configuration> m_configs;

    public MockConfigAdmin() {
        m_configs = new HashMap<>();
    }

    public Configuration createFactoryConfiguration(String factoryPID) throws IOException {
        return createFactoryConfiguration(factoryPID, null);
    }

    public Configuration createFactoryConfiguration(String factoryPID, String location) throws IOException {
        Configuration config = m_configs.get(factoryPID);
        if (config == null) {
            config = new MockConfiguration(this);
            m_configs.put(factoryPID, config);
        }
        return config;
    }

    public Configuration getConfiguration(String pid) throws IOException {
        return getConfiguration(pid, null);
    }

    public Configuration getConfiguration(String pid, String location) throws IOException {
        Configuration config = m_configs.get(pid);
        if (config == null) {
            config = new MockConfiguration(this);
            m_configs.put(pid, config);
        }
        return config;
    }

    public Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException {
        Collection<Configuration> configs = m_configs.values();
        return configs.toArray(new Configuration[configs.size()]);
    }

    abstract void configDeleted(MockConfiguration config);

    abstract void configUpdated(MockConfiguration config);
}
