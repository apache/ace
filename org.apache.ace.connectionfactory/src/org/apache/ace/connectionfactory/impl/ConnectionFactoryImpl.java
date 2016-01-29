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
package org.apache.ace.connectionfactory.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType;
import org.apache.ace.connectionfactory.impl.UrlCredentialsFactory.MissingValueException;
import org.apache.commons.codec.binary.Base64;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.useradmin.User;

/**
 * Provides a default implementation for {@link ConnectionFactory} based on the standard <code>java.net</code>
 * implementation of {@link URLConnection}.
 */
public class ConnectionFactoryImpl implements ConnectionFactory, ManagedServiceFactory {

    public static final String FACTORY_PID = "org.apache.ace.connectionfactory";

    private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";

    private final Map<String /* config PID */, UrlCredentials> m_credentialMapping;

    /**
     * Creates a new {@link ConnectionFactoryImpl}.
     */
    public ConnectionFactoryImpl() {
        m_credentialMapping = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    public URLConnection createConnection(URL url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null!");
        }

        URLConnection conn = url.openConnection();

        UrlCredentials creds = getCredentials(url);
        if (creds != null) {
            supplyCredentials(conn, creds);
        }

        return conn;
    }

    /**
     * {@inheritDoc}
     */
    public URLConnection createConnection(URL url, User user) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null!");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null!");
        }

        URLConnection conn = url.openConnection();

        UrlCredentials creds = getCredentials(url);
        if (creds != null) {
            // TODO apply user!
            supplyCredentials(conn, creds);
        }

        return conn;
    }

    /**
     * {@inheritDoc}
     */
    public void deleted(String pid) {
        synchronized (m_credentialMapping) {
            m_credentialMapping.remove(pid);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "HTTP Connection Factory";
    }

    /**
     * {@inheritDoc}
     */
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        UrlCredentials creds;
        synchronized (m_credentialMapping) {
            creds = m_credentialMapping.get(pid);
        }

        try {
            creds = UrlCredentialsFactory.getCredentials(properties);

            synchronized (m_credentialMapping) {
                m_credentialMapping.put(pid, creds);
            }
        }
        catch (MissingValueException e) {
            throw new ConfigurationException(e.getProperty(), e.getMessage());
        }
    }

    /**
     * Returns the credentials to access the given URL.
     * 
     * @param url
     *            the URL to find the credentials for, cannot be <code>null</code>.
     * @return a {@link UrlCredentials} instance for the given URL, or <code>null</code> if none were found, or if none
     *         were necessary.
     */
    final UrlCredentials getCredentials(URL url) {
        Collection<UrlCredentials> creds;
        synchronized (m_credentialMapping) {
            creds = new ArrayList<>(m_credentialMapping.values());
        }

        for (UrlCredentials c : creds) {
            if (c.matches(url)) {
                return c;
            }
        }

        return null;
    }

    /**
     * Returns the authorization header for HTTP Basic Authentication.
     * 
     * @param values
     *            the credential values to supply, cannot be <code>null</code> and should be an array of two elements.
     * @return a string that denotes the basic authentication header ("Basic " + encoded credentials), never
     *         <code>null</code>.
     */
    final String getBasicAuthCredentials(Object[] values) {
        if ((values == null) || values.length < 2) {
            throw new IllegalArgumentException("Insufficient credentials passed: expected 2 values!");
        }

        StringBuilder sb = new StringBuilder();
        if (values[0] instanceof String) {
            sb.append((String) values[0]);
        }
        else if (values[0] instanceof byte[]) {
            sb.append(new String((byte[]) values[0]));
        }
        sb.append(':');
        if (values[1] instanceof String) {
            sb.append((String) values[1]);
        }
        else if (values[1] instanceof byte[]) {
            sb.append(new String((byte[]) values[1]));
        }

        return "Basic " + new String(Base64.encodeBase64(sb.toString().getBytes()));
    }

    /**
     * Applies basic authentication to the given connection, if it is a {@link HttpURLConnection}.
     * 
     * @param conn
     *            the connection to apply basic authentication to;
     * @param values
     *            the credentials to apply.
     */
    private void applyBasicAuthentication(URLConnection conn, Object[] values) {
        if (conn instanceof HttpURLConnection) {
            conn.setRequestProperty(HTTP_HEADER_AUTHORIZATION, getBasicAuthCredentials(values));
        }
    }

    /**
     * Applies the use of client certificates to the given connection, if it a {@link HttpsURLConnection}.
     * 
     * @param conn
     *            the connection to apply client certs to;
     * @param values
     *            the credentials to apply.
     */
    private void applyClientCertificate(URLConnection conn, Object[] values) {
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(((SSLContext) values[0]).getSocketFactory());
        }
    }

    /**
     * Supplies the actual credentials to the given {@link URLConnection}.
     * 
     * @param conn
     *            the connection to supply the credentials to, cannot be <code>null</code>;
     * @param urlCreds
     *            the URL credentials to supply, cannot be <code>null</code>.
     * @throws IOException
     *             in case of I/O problems.
     */
    private void supplyCredentials(URLConnection conn, UrlCredentials urlCreds) throws IOException {
        final AuthType type = urlCreds.getType();
        final Object[] creds = urlCreds.getCredentials();

        if (AuthType.BASIC.equals(type)) {
            applyBasicAuthentication(conn, creds);
        }
        else if (AuthType.CLIENT_CERT.equals(type)) {
            applyClientCertificate(conn, creds);
        }
        else if (!AuthType.NONE.equals(type)) {
            throw new IllegalArgumentException("Unknown authentication type: " + type);
        }
    }
}
