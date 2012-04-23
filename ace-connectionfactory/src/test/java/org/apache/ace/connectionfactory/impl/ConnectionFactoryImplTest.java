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

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import org.junit.Test;

/**
 * Test cases for {@link ConnectionFactoryImpl}.
 */
public class ConnectionFactoryImplTest {

    private static final URL TEST_URL;
    
    static {
        try {
            TEST_URL = new URL("http://localhost:8080/");
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#createConnection(java.net.URL)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateConnectionNullUrlFail() throws Exception {
        new ConnectionFactoryImpl().createConnection(null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#createConnection(java.net.URL, org.osgi.service.useradmin.User)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateConnectionNullUserFail() throws Exception {
        new ConnectionFactoryImpl().createConnection(new URL("file:///tmp/foo"), null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#createConnection(java.net.URL, org.osgi.service.useradmin.User)}.
     */
    @Test
    public void testCreateConnectionOk() throws Exception {
        URLConnection conn = new ConnectionFactoryImpl().createConnection(new URL("file:///tmp/foo"));
        assertNotNull(conn);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#deleted(java.lang.String)}.
     */
    @Test
    public void testDeleted() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();

        Properties props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        UrlCredentials credentials = connFactory.getCredentials(TEST_URL);
        assertNotNull(credentials);
        
        connFactory.deleted("pid1");
        
        credentials = connFactory.getCredentials(TEST_URL);
        assertNull(credentials);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#updated(java.lang.String, java.util.Dictionary)}.
     */
    @Test
    public void testUpdatedInsertsCredentialsOk() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();
        
        UrlCredentials credentials = connFactory.getCredentials(TEST_URL);
        assertNull(credentials);
        
        Properties props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        credentials = connFactory.getCredentials(TEST_URL);
        assertNotNull(credentials);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#updated(java.lang.String, java.util.Dictionary)}.
     */
    @Test
    public void testUpdatedUpdatesCredentialsOk() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();

        Properties props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        UrlCredentials credentials1 = connFactory.getCredentials(TEST_URL);
        assertNotNull(credentials1);
        
        URL newURL = new URL("http://localhost:8181/test/");
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, newURL.toExternalForm());

        connFactory.updated("pid1", props);

        UrlCredentials credentials2 = connFactory.getCredentials(TEST_URL);
        assertNull(credentials2);

        credentials2 = connFactory.getCredentials(newURL);
        assertNotNull(credentials2);
        
        assertNotSame(credentials1, credentials2);
    }

    /**
     * @return a dictionary containing a configuration for basic authentication, never <code>null</code>.
     */
    private Properties createBasicAuthConfig(String url) {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, url);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "foo");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");
        return props;
    }
}
