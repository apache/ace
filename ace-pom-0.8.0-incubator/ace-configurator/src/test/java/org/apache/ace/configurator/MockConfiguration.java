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

    @SuppressWarnings("unchecked")
    private Dictionary m_properties = null;
    private boolean m_isDeleted = false;

    public void delete() throws IOException {
        m_isDeleted = true;
    }

    public String getBundleLocation() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getFactoryPid() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getPid() {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("unchecked")
    public synchronized Dictionary getProperties() {
        return m_properties;
    }

    public void setBundleLocation(String arg0) {
        // TODO Auto-generated method stub

    }

    public void update() throws IOException {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("unchecked")
    public synchronized void update(Dictionary newConfiguration) throws IOException {
            m_properties = newConfiguration;
    }

    public boolean isDeleted() {
        return m_isDeleted;
    }

}
