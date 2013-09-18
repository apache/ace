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

import static org.apache.ace.agent.AgentConstants.EVENT_AGENT_CONFIG_CHANGED;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_AUTHTYPE;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_KEYFILE;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_KEYPASS;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_PASSWORD;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_SSL_PROTOCOL;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_TRUSTFILE;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_TRUSTPASS;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONNECTION_USERNAME;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.EventListener;

/**
 * Default thread-safe {@link ConnectionHandler} implementation with support for BASIC authentication and HTTPS client
 * certificates.
 */
public class ConnectionHandlerImpl extends ComponentBase implements ConnectionHandler, EventListener {

    private static class UrlCredentials {
        private final Types m_type;
        private final Object[] m_credentials;

        public UrlCredentials(Types type, Object... credentials) {
            m_type = type;
            m_credentials = credentials.clone();
        }

        public Object[] getCredentials() {
            return m_credentials;
        }

        public Types getType() {
            return m_type;
        }
    }

    private static final UrlCredentials EMPTY_CREDENTIALS = new UrlCredentials(Types.NONE);
    private static final String DEFAULT_PROTOCOL = "TLS";

    private volatile UrlCredentials m_credentials;

    public ConnectionHandlerImpl() {
        super("connection");
    }

    @Override
    public URLConnection getConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();

        UrlCredentials credentials = m_credentials;
        if (credentials != null) {
            if (Types.BASIC == credentials.getType()) {
                applyBasicAuthentication(connection, credentials.getCredentials());
            }
            else if (Types.CLIENTCERT == credentials.getType()) {
                applyClientCertificate(connection, credentials.getCredentials());
            }
        }

        return connection;
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            m_credentials = getCredentials(payload);
        }
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);
    }

    private void applyBasicAuthentication(URLConnection conn, Object[] values) {
        if (conn instanceof HttpURLConnection) {
            String encodedCreds = (String) values[0];
            conn.setRequestProperty("Authorization", "Basic ".concat(encodedCreds));
        }
    }

    private void applyClientCertificate(URLConnection conn, Object[] values) {
        if (conn instanceof HttpsURLConnection) {
            SSLContext sslContext = (SSLContext) values[0];
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslContext.getSocketFactory());
        }
    }

    private String encodeBasicAuthCredentials(String username, String password) {
        StringBuilder sb = new StringBuilder();
        if (username != null) {
            sb.append(username);
        }
        sb.append(':');
        if (password != null) {
            sb.append(password);
        }

        return DatatypeConverter.printBase64Binary(sb.toString().getBytes());
    }

    private UrlCredentials getCredentials(Map<String, String> config) {
        String configValue = config.get(CONFIG_CONNECTION_AUTHTYPE);

        Types authType = Types.parseType(configValue);
        switch (authType) {
            case BASIC:
                String username = config.get(CONFIG_CONNECTION_USERNAME);
                String password = config.get(CONFIG_CONNECTION_PASSWORD);

                return new UrlCredentials(authType, encodeBasicAuthCredentials(username, password));

            case CLIENTCERT:
                String keystoreFile = config.get(CONFIG_CONNECTION_KEYFILE);
                String keystorePass = config.get(CONFIG_CONNECTION_KEYPASS);
                String truststoreFile = config.get(CONFIG_CONNECTION_TRUSTFILE);
                String truststorePass = config.get(CONFIG_CONNECTION_TRUSTPASS);
                String sslProtocol = config.get(CONFIG_CONNECTION_SSL_PROTOCOL);
                if (sslProtocol == null || "".equals(sslProtocol.trim())) {
                    sslProtocol = DEFAULT_PROTOCOL;
                }

                try {
                    KeyManager[] keyManagers = getKeyManagerFactory(keystoreFile, keystorePass);
                    TrustManager[] trustManagers = getTrustManagerFactory(truststoreFile, truststorePass);

                    SSLContext context = SSLContext.getInstance(sslProtocol);
                    context.init(keyManagers, trustManagers, new SecureRandom());

                    return new UrlCredentials(authType, context);
                }
                catch (Exception e) {
                    logError("Failed to get credentials for client certificate!", e);
                }

            case NONE:
            default:
                return EMPTY_CREDENTIALS;
        }
    }

    private KeyManager[] getKeyManagerFactory(String keystoreFile, String storePass) throws IOException, GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        InputStream is = null;
        try {
            is = new FileInputStream(keystoreFile);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(is, storePass.toCharArray());

            kmf.init(ks, storePass.toCharArray());

            return kmf.getKeyManagers();
        }
        finally {
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException e) {
                // Ignore; we're only reading from it...
            }
        }
    }

    private TrustManager[] getTrustManagerFactory(String truststoreFile, String storePass) throws IOException, GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        InputStream is = null;
        try {
            is = new FileInputStream(truststoreFile);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(is, storePass.toCharArray());

            tmf.init(ks);

            return tmf.getTrustManagers();
        }
        finally {
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException e) {
                // Ignore; we're only reading from it...
            }
        }
    }
}
