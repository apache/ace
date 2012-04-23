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

import java.net.URL;

import org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType;
import org.junit.Test;

/**
 * Test cases for {@link UrlCredentials}.
 */
public class UrlCredentialsTest {

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#UrlCredentials(java.net.URL)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUrlCredentialsNullURLFail() throws Exception {
        new UrlCredentials(null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#UrlCredentials(java.net.URL)}.
     */
    @Test
    public void testUrlCredentialsURLOk() throws Exception {
        new UrlCredentials(new URL("http://localhost:8080/"));
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#UrlCredentials(org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType, java.net.URL, java.lang.Object[])}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUrlCredentialsNullTypeFail() throws Exception {
        new UrlCredentials(null, new URL("http://localhost:8080/"));
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#UrlCredentials(org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType, java.net.URL, java.lang.Object[])}.
     */
    @Test
    public void testUrlCredentialsTypeAndURLOk() throws Exception {
        new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"));
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#matches(java.net.URL)}.
     */
    @Test
    public void testMatchesNullURLOk() throws Exception {
        UrlCredentials creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"));
        assertFalse(creds.matches(null));
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#matches(java.net.URL)}.
     */
    @Test
    public void testMatchesValidURLOk() throws Exception {
        UrlCredentials creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"));
        assertTrue(creds.matches(new URL("http://localhost:8080/obr")));
        assertFalse(creds.matches(new URL("http://localhost:8080")));
        assertFalse(creds.matches(new URL("http://localhost:8081/")));
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#getCredentials()}.
     */
    @Test
    public void testGetCredentialsOk() throws Exception {
        UrlCredentials creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"));
        assertArrayEquals(new Object[0], creds.getCredentials());

        creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"), "foo");
        assertArrayEquals(new Object[] { "foo" }, creds.getCredentials());

        creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"), (Object[]) null );
        assertArrayEquals(new Object[0], creds.getCredentials());

        creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"), (Object) null);
        assertArrayEquals(new Object[] { null }, creds.getCredentials());
    }
}
