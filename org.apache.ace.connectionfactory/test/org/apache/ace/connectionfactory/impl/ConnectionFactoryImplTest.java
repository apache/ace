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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.testng.annotations.Test;

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
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateConnectionNullUrlFail() throws Exception {
        new ConnectionFactoryImpl().createConnection(null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#createConnection(java.net.URL, org.osgi.service.useradmin.User)}.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateConnectionNullUserFail() throws Exception {
        new ConnectionFactoryImpl().createConnection(new URL("file:///tmp/foo"), null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#createConnection(java.net.URL, org.osgi.service.useradmin.User)}.
     */
    @Test()
    public void testCreateConnectionOk() throws Exception {
        URLConnection conn = new ConnectionFactoryImpl().createConnection(new URL("file:///tmp/foo"));
        assert conn != null : "Expected valid connection to be created!";
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#deleted(java.lang.String)}.
     */
    @Test()
    public void testDeleted() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();

        Dictionary<String, ?> props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        UrlCredentials credentials = connFactory.getCredentials(TEST_URL);
        assert credentials != null : "Expected valid credentials to be found!";

        connFactory.deleted("pid1");
        
        credentials = connFactory.getCredentials(TEST_URL);
        assert credentials == null : "Expected no credentials to be found!";
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#getBasicAuthCredentials(UrlCredentials)}.
     */
    @Test()
    public void testGetBasicAuthCredentialsOk() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();

        Dictionary<String, ?> props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);

        UrlCredentials credentials = connFactory.getCredentials(TEST_URL);
        assert credentials != null : "Expected valid credentials to be found!";

        String header = new ConnectionFactoryImpl().getBasicAuthCredentials(credentials.getCredentials());
        assert header != null : "Expected valid HTTP header to be returned!";
        assert header.equals(header.trim()) : "Expected HTTP header not to contain any leading/trailing whitespace!";
        assert "Basic Zm9vOmJhcg==".equals(header) : "Expected HTTP header to be constant!";
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#updated(java.lang.String, java.util.Dictionary)}.
     */
    @Test()
    public void testUpdatedInsertsCredentialsOk() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();
        
        UrlCredentials credentials = connFactory.getCredentials(TEST_URL);
        assert credentials == null : "Expected no credentials to be found!";
        
        Dictionary<String, ?> props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        credentials = connFactory.getCredentials(TEST_URL);
        assert credentials != null : "Expected valid credentials to be found!";
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.ConnectionFactoryImpl#updated(java.lang.String, java.util.Dictionary)}.
     */
    @Test()
    public void testUpdatedUpdatesCredentialsOk() throws Exception {
        ConnectionFactoryImpl connFactory = new ConnectionFactoryImpl();

        Dictionary<String, Object> props = createBasicAuthConfig(TEST_URL.toExternalForm());

        connFactory.updated("pid1", props);
        
        UrlCredentials credentials1 = connFactory.getCredentials(TEST_URL);
        assert credentials1 != null : "Expected valid credentials to be found!";
        
        URL newURL = new URL("http://localhost:8181/test/");
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, newURL.toExternalForm());

        connFactory.updated("pid1", props);

        UrlCredentials credentials2 = connFactory.getCredentials(TEST_URL);
        assert credentials2 == null : "Expected no credentials to be found!";

        credentials2 = connFactory.getCredentials(newURL);
        assert credentials2 != null : "Expected valid credentials to be found!";
        
        assert credentials1 != credentials2 && !credentials1.equals(credentials2) : "Expected not the same credentials to be returned!";
    }

    /**
     * @return a dictionary containing a configuration for basic authentication, never <code>null</code>.
     */
    private Dictionary<String, Object> createBasicAuthConfig(String url) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, url);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "foo");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");
        return props;
    }
}
