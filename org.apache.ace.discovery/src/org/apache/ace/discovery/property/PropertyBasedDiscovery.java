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
package org.apache.ace.discovery.property;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.discovery.DiscoveryConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Simple implementation of the <code>Discovery</code> interface. It 'discovers' the Provisioning Server by implementing
 * the <code>ManagedService</code> and having the location configured by <code>ConfigurationAdmin</code>. If no
 * configuration or a <code>null</code> configuration has been supplied by <code>ConfigurationAdmin</code> the location
 * stored in <code>TargetConstants.DISCOVERY_DEFAULT_URL</code> will be used.
 */
public class PropertyBasedDiscovery implements Discovery, ManagedService {
    private final Object LOCK = new Object();
    public volatile LogService m_log; /* will be injected by dependencymanager */
    private URL m_serverURL; /* managed by configadmin */

    public PropertyBasedDiscovery() {
    }

    public PropertyBasedDiscovery(String url) {
        try {
            setURL(url);
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot create instance, invalid URL argument", e);
        }
    }

    public URL getURL() {
        synchronized (LOCK) {
            return m_serverURL;
        }
    }

    public void setURL(String id) throws MalformedURLException {
        setURL(new URL(id));
    }

    public void setURL(URL id) {
        synchronized (LOCK) {
            m_serverURL = id;
        }
    }

    public synchronized void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        try {
            if (dictionary != null) {
                setURL(new URL((String) dictionary.get(DiscoveryConstants.DISCOVERY_URL_KEY)));
            }
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(DiscoveryConstants.DISCOVERY_URL_KEY, "Malformed URL", e);
        }
    }

    public synchronized URL discover() {
        return getURL();
    }
}
