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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DiscoveryHandler;

/**
 * Default discovery handler that reads the serverURL(s) from the configuration using key {@link DISCOVERY_CONFIG_KEY}.
 * 
 */
public class DiscoveryHandlerImpl extends HandlerBase implements DiscoveryHandler {

    public static final String COMPONENT_IDENTIFIER = "discovery";
    public static final String CONFIG_KEY_BASE = ConfigurationHandlerImpl.CONFIG_KEY_NAMESPACE + "." + COMPONENT_IDENTIFIER;

    public DiscoveryHandlerImpl() {
        super(COMPONENT_IDENTIFIER);
    }

    /**
     * Configuration key for the default discovery handler. The value must be a comma-separated list of valid base
     * server URLs.
     */
    public static final String CONFIG_KEY_SERVERURLS = CONFIG_KEY_BASE + ".serverUrls";
    public static final String CONFIG_DEFAULT_SERVERURLS = "http://localhost:8080";

    // TODO Pretty naive implementation below. It always takes the first configured URL it can connect to and is not
    // thread-safe.
    @Override
    public URL getServerUrl() {
        ConfigurationHandler configurationHandler = getAgentContext().getConfigurationHandler();

        String configValue = configurationHandler.get(CONFIG_KEY_SERVERURLS, CONFIG_DEFAULT_SERVERURLS);
        URL url = null;
        if (configValue.indexOf(",") == -1) {
            url = checkURL(configValue.trim());
        }
        else {
            for (String configValuePart : configValue.split(",")) {
                if (url == null) {
                    url = checkURL(configValuePart.trim());
                }
            }
        }
        if (url == null) {
            logWarning("No connectable serverUrl available");
        }
        return url;
    }

    private static final long CACHE_TIME = 1000;

    private static class CheckedURL {
        URL url;
        long timestamp;

        public CheckedURL(URL url, long timestamp) {
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, CheckedURL> m_checkedURLs = new HashMap<String, DiscoveryHandlerImpl.CheckedURL>();

    private URL checkURL(String serverURL) {

        CheckedURL checked = m_checkedURLs.get(serverURL);
        if (checked != null && checked.timestamp > (System.currentTimeMillis() - CACHE_TIME)) {
            logDebug("Returning cached serverURL: " + checked.url.toExternalForm());
            return checked.url;
        }
        try {
            URL url = new URL(serverURL);
            tryConnect(url);
            logDebug("Succesfully connected to  serverURL: %s", serverURL);
            m_checkedURLs.put(serverURL, new CheckedURL(url, System.currentTimeMillis()));
            return url;
        }
        catch (IOException e) {
            logDebug("Failed to connect to serverURL: " + serverURL);
            return null;
        }
    }

    private void tryConnect(URL serverURL) throws IOException {
        URLConnection connection = null;
        try {
            connection = getAgentContext().getConnectionHandler().getConnection(serverURL);
            connection.connect();
        }
        finally {
            if (connection != null && connection instanceof HttpURLConnection)
                ((HttpURLConnection) connection).disconnect();
        }
    }
}
