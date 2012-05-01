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

import java.net.URL;
import java.util.Arrays;

/**
 * Small container for holding URL credentials.
 */
final class UrlCredentials {

    static enum AuthType {
        /** Indicates no authentication. */
        NONE,
        /** Indicates basic HTTP authentication. */
        BASIC,
        /** Indicates the use of client certificates. */
        CLIENT_CERT;
    }

    private final AuthType m_type;
    private final URL m_baseURL;
    private final Object[] m_credentials;

    /**
     * Creates a new, anonymous, {@link UrlCredentials} instance.
     * 
     * @param baseURL the base URL for which to apply the credentials, cannot be <code>null</code>.
     */
    public UrlCredentials(URL baseURL) {
        this(AuthType.NONE, baseURL);
    }

    /**
     * Creates a new {@link UrlCredentials} instance.
     * 
     * @param type the authentication type to use for the authentication of the URL, cannot be <code>null</code>;
     * @param baseURL the base URL for which to apply the credentials, cannot be <code>null</code>;
     * @param credentials the credentials to use, cannot be <code>null</code>, but may be empty.
     */
    public UrlCredentials(AuthType type, URL baseURL, Object... credentials) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null!");
        }
        if (baseURL == null) {
            throw new IllegalArgumentException("BaseURL cannot be null!");
        }
        m_type = type;
        m_baseURL = baseURL;
        m_credentials = (credentials == null) ? new Object[0] : credentials.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        UrlCredentials other = (UrlCredentials) obj;
        if (m_type != other.m_type) {
            return false;
        }
        if (!m_baseURL.equals(other.m_baseURL)) {
            return false;
        }
        if (!Arrays.equals(m_credentials, other.m_credentials)) {
            return false;
        }
        return true;
    }
    
    /**
     * Returns whether or not the given URL can be mapped to our own base URL.
     * 
     * @param url the URL to map, may be <code>null</code> in which case <code>false</code> will be returned.
     * @return <code>true</code> if the given URL maps to our base URL, <code>false</code> otherwise.
     */
    public boolean matches(URL url) {
        if (url == null) {
            return false;
        }
        
        String baseURL = m_baseURL.toExternalForm();
        return url.toExternalForm().startsWith(baseURL);
    }

    /**
     * Returns the credentials for a URL.
     * 
     * @return the credentials, never <code>null</code>.
     */
    public Object[] getCredentials() {
        return m_credentials.clone();
    }

    /**
     * Returns the authentication type.
     * 
     * @return the type of authentication to use.
     */
    public AuthType getType() {
        return m_type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((m_type == null) ? 0 : m_type.hashCode());
        result = prime * result + ((m_baseURL == null) ? 0 : m_baseURL.hashCode());
        result = prime * result + Arrays.hashCode(m_credentials);
        return result;
    }

    /**
     * @return the base URL these credentials apply to, cannot be <code>null</code>.
     */
    final URL getBaseURL() {
        return m_baseURL;
    }
}
