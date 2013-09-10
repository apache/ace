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
import static org.apache.ace.agent.impl.InternalConstants.AGENT_CONFIG_CHANGED;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.EventListener;

/**
 * Default thread-safe {@link DiscoveryHandler} implementation that reads the serverURL(s) from the configuration using
 * key {@link CONFIG_DISCOVERY_SERVERURLS}. If the {@link CONFIG_DISCOVERY_CHECKING} flag is a connection is opened to
 * test whether a serverURL is available before it is returned.
 */
public class DiscoveryHandlerImpl extends ComponentBase implements DiscoveryHandler, EventListener {

    private static class CheckedURL {
        /** cache timeout in milliseconds. */
        private static final long CACHE_TIME = 30000;

        public final URL m_url;
        private final AtomicLong m_timestamp;
        private final AtomicBoolean m_blackListed;

        public CheckedURL(URL url) {
            m_url = url;
            m_blackListed = new AtomicBoolean(false);
            m_timestamp = new AtomicLong(0L);
        }

        public void available() {
            m_blackListed.set(false);
            m_timestamp.set(System.currentTimeMillis());
        }

        public void blacklist() {
            m_blackListed.set(true);
            m_timestamp.set(System.currentTimeMillis());
        }

        public boolean isBlacklisted() {
            boolean result = m_blackListed.get();
            if (result) {
                if (!isRecentlyChecked()) {
                    // lift the ban...
                    m_blackListed.compareAndSet(result, false);
                    result = false;
                }
            }
            return result;
        }

        public boolean isRecentlyChecked() {
            return m_timestamp.get() > (System.currentTimeMillis() - CACHE_TIME);
        }
    }

    /** default server URL. */
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    /** whether or not to test server URLs. */
    private static final boolean DEFAULT_CHECK_SERVER_ULRS = false;

    private final List<String> m_urls;
    private final AtomicBoolean m_checkURLs;
    private final ConcurrentMap<String, CheckedURL> m_availableURLs;

    public DiscoveryHandlerImpl() {
        super("discovery");

        m_availableURLs = new ConcurrentHashMap<String, CheckedURL>();
        m_checkURLs = new AtomicBoolean(DEFAULT_CHECK_SERVER_ULRS);
        m_urls = new ArrayList<String>(Arrays.asList(DEFAULT_SERVER_URL));
    }

    /**
     * Returns the first available URL, based on the order specified in the configuration.
     * 
     * @return a (valid) server URL, or <code>null</code> in case no server URL was valid.
     */
    @Override
    public URL getServerUrl() {
        String[] urls;
        synchronized (m_urls) {
            urls = new String[m_urls.size()];
            m_urls.toArray(urls);
        }
        boolean checking = m_checkURLs.get();

        URL url = null;
        for (String urlValue : urls) {
            if ((url = getURL(urlValue, checking)) != null) {
                break;
            }
        }

        if (url == null) {
            logWarning("No valid server URL discovered?!");
        }

        return url;
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (AGENT_CONFIG_CHANGED.equals(topic)) {
            String value = payload.get(CONFIG_DISCOVERY_SERVERURLS);
            if (value != null && !"".equals(value.trim())) {
                String[] urls = value.trim().split("\\s*,\\s*");

                synchronized (m_urls) {
                    m_urls.clear();
                    m_urls.addAll(Arrays.asList(urls));
                }
                // Assume nothing about the newly configured URLs...
                m_availableURLs.clear();
            }

            value = payload.get(CONFIG_DISCOVERY_CHECKING);
            if (value != null) {
                boolean checkURLs = Boolean.parseBoolean(value);
                // last one wins...
                m_checkURLs.set(checkURLs);
            }
        }
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);

        m_availableURLs.clear();
    }

    private URL getURL(String serverURL, boolean checkURL) {
        CheckedURL checkedURL = null;
        URL result = null;

        try {
            logDebug("Start getting URL for : %s", serverURL);

            checkedURL = m_availableURLs.get(serverURL);
            if (checkedURL == null) {
                checkedURL = new CheckedURL(new URL(serverURL));

                CheckedURL putResult = m_availableURLs.putIfAbsent(serverURL, checkedURL);
                if (putResult != null) {
                    // lost the put, make sure to use the correct object...
                    checkedURL = putResult;
                }
            }

            if (checkedURL.isBlacklisted()) {
                logDebug("Ignoring blacklisted serverURL: %s", serverURL);
                // Take the short way home...
                return null;
            }

            result = checkedURL.m_url;
            if (checkURL && !checkedURL.isRecentlyChecked()) {
                logDebug("Trying to connect to serverURL: %s", serverURL);

                tryConnect(checkedURL.m_url);
                // no exception was thrown trying to connect to the URL, so assume it's available...
                checkedURL.available();

                logDebug("Succesfully connected to serverURL: %s", serverURL);
            }
        }
        catch (MalformedURLException e) {
            logWarning("Ignoring invalid/malformed serverURL: %s", serverURL);
            // No need to blacklist for this case, we're trying to create a CheckedURL which isn't present...
            result = null;
        }
        catch (IOException e) {
            logWarning("Temporarily blacklisting unavailable serverURL: %s", serverURL);
            if (checkedURL != null) {
                checkedURL.blacklist();
            }
            result = null;
        }

        return result;
    }

    private void tryConnect(URL serverURL) throws IOException {
        URLConnection connection = null;
        try {
            connection = getConnectionHandler().getConnection(serverURL);
            connection.connect();
        }
        finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }
}
