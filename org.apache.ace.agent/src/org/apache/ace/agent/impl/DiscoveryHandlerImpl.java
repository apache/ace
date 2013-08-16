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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.DiscoveryHandler;

/**
 * Default discovery handler that reads the serverURL(s) from the configuration using key {@link DISCOVERY_CONFIG_KEY}.
 * 
 */
public class DiscoveryHandlerImpl implements DiscoveryHandler {

    /**
     * Configuration key for the default discovery handler. The value must be a comma-separated list of valid base
     * server URLs.
     */
    // TODO move to and validate in config handler?
    public static final String DISCOVERY_CONFIG_KEY = "agent.discovery";

    private final AgentContext m_agentContext;

    public DiscoveryHandlerImpl(AgentContext agentContext) throws Exception {
        m_agentContext = agentContext;
    }

    // TODO Pretty naive implementation below. It always takes the first configurred URL it can connect to and is not
    // thread-safe.
    @Override
    public URL getServerUrl() {
        String configValue = m_agentContext.getConfigurationHandler().getMap().get(DISCOVERY_CONFIG_KEY);
        if (configValue == null || configValue.equals(""))
            try {
                return new URL("http://localhost:8080");
            }
            catch (MalformedURLException e) {
                e.printStackTrace();
            }
        if (configValue.indexOf(",") == -1) {
            return checkURL(configValue.trim());
        }
        for (String configValuePart : configValue.split(",")) {
            URL url = checkURL(configValuePart.trim());
            if (url != null)
                return url;
        }
        return null;
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
            return checked.url;
        }
        try {
            URL url = new URL(serverURL);
            tryConnect(url);
            m_checkedURLs.put(serverURL, new CheckedURL(url, System.currentTimeMillis()));
            return url;
        }
        catch (IOException e) {
            // TODO log
            return null;
        }
    }

    private void tryConnect(URL serverURL) throws IOException {
        URLConnection connection = null;
        try {
            connection = m_agentContext.getConnectionHandler().getConnection(serverURL);
            connection.connect();
        }
        finally {
            if (connection != null && connection instanceof HttpURLConnection)
                ((HttpURLConnection) connection).disconnect();
        }
    }
}
