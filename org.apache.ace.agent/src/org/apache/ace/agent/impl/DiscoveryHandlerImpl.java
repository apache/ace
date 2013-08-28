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

import static org.apache.ace.agent.AgentConstants.CONFIG_DISCOVERY_CHECKING;
import static org.apache.ace.agent.AgentConstants.CONFIG_DISCOVERY_SERVERURLS;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.DiscoveryHandler;

/**
 * Default thread-safe {@link DiscoveryHandler} implementation that reads the serverURL(s) from the configuration using
 * key {@link CONFIG_DISCOVERY_SERVERURLS}. If the {@link CONFIG_DISCOVERY_CHECKING} flag is a connection is opened to
 * test whether a serverURL is available before it is returned.
 */
public class DiscoveryHandlerImpl extends ComponentBase implements DiscoveryHandler {

    private final Map<String, CheckedURL> m_availableURLs = new HashMap<String, DiscoveryHandlerImpl.CheckedURL>();
    private final Map<String, CheckedURL> m_blacklistedURLs = new HashMap<String, DiscoveryHandlerImpl.CheckedURL>();

    private static final long CACHE_TIME = 2000;

    public DiscoveryHandlerImpl() {
        super("discovery");
    }

    @Override
    protected void onStop() throws Exception {
        m_availableURLs.clear();
        m_blacklistedURLs.clear();
    }

    // TODO Pretty naive implementation below. It always takes the first configured URL it can connect to and is not
    // thread-safe.
    @Override
    public URL getServerUrl() {

        String configValue = getConfigurationHandler().get(CONFIG_DISCOVERY_SERVERURLS, "http://localhost:8080");
        boolean checking = getConfigurationHandler().getBoolean(CONFIG_DISCOVERY_CHECKING, false);

        URL url = null;
        if (configValue.indexOf(",") == -1) {
            url = getURL(configValue.trim(), checking);
        }
        else {
            for (String configValuePart : configValue.split(",")) {
                url = getURL(configValuePart.trim(), checking);
                if (url != null) {
                    break;
                }
            }
        }
        if (url == null) {
            logWarning("No connectable serverUrl available");
        }
        return url;
    }

    private static class CheckedURL {
        URL url;
        long timestamp;

        public CheckedURL(URL url, long timestamp) {
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private URL getURL(String serverURL, boolean checking) {

        URL url = null;
        try {
            CheckedURL blackListed = m_blacklistedURLs.get(serverURL);
            if (blackListed != null && blackListed.timestamp > (System.currentTimeMillis() - CACHE_TIME)) {
                logDebug("Ignoring blacklisted serverURL: " + serverURL);
                return null;
            }

            url = new URL(serverURL);
            if (!checking) {
                return url;
            }

            CheckedURL available = m_availableURLs.get(serverURL);
            if (available != null && available.timestamp > (System.currentTimeMillis() - CACHE_TIME)) {
                logDebug("Returning available serverURL: " + available.url.toExternalForm());
                return available.url;
            }

            tryConnect(url);
            logDebug("Succesfully connected to  serverURL: %s", serverURL);
            m_availableURLs.put(serverURL, new CheckedURL(url, System.currentTimeMillis()));
            return url;
        }
        catch (MalformedURLException e) {
            logError("Temporarily blacklisting malformed serverURL: " + serverURL);
            m_blacklistedURLs.put(serverURL, new CheckedURL(url, System.currentTimeMillis()));
            return null;
        }
        catch (IOException e) {
            logWarning("Temporarily blacklisting unavailable serverURL: " + serverURL);
            m_blacklistedURLs.put(serverURL, new CheckedURL(url, System.currentTimeMillis()));
            return null;
        }
    }

    private void tryConnect(URL serverURL) throws IOException {
        URLConnection connection = null;
        try {
            connection = getConnectionHandler().getConnection(serverURL);
            connection.connect();
        }
        finally {
            if (connection != null && connection instanceof HttpURLConnection)
                ((HttpURLConnection) connection).disconnect();
        }
    }
}
