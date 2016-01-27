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
import java.util.Dictionary;

import org.osgi.service.cm.Configuration;

public class MockConfiguration implements Configuration {
    private final MockConfigAdmin m_ca;
    private Dictionary<String, Object> m_properties = null;
    private boolean m_isDeleted = false;

    public MockConfiguration(MockConfigAdmin ca) {
        m_ca = ca;
    }

    public void delete() throws IOException {
        m_isDeleted = true;
        m_ca.configDeleted(this);
    }

    public String getBundleLocation() {
        return null;
    }

    public String getFactoryPid() {
        return null;
    }

    public String getPid() {
        return null;
    }

    public synchronized Dictionary<String, Object> getProperties() {
        return m_properties;
    }

    public boolean isDeleted() {
        return m_isDeleted;
    }

    public void setBundleLocation(String location) {
    }

    public void update() throws IOException {
        m_ca.configUpdated(this);
    }

    @SuppressWarnings("unchecked")
    public synchronized void update(Dictionary<String, ?> newConfiguration) throws IOException {
        m_properties = (Dictionary<String, Object>) newConfiguration;
        m_ca.configUpdated(this);
    }
}
