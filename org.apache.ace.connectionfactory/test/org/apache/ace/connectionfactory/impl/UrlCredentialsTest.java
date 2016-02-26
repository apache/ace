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

import org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType;
import org.testng.annotations.Test;

/**
 * Test cases for {@link UrlCredentials}.
 */
public class UrlCredentialsTest {

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#UrlCredentials(java.net.URL)}.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
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
    @Test(expectedExceptions = IllegalArgumentException.class)
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
        assert creds.matches(null) == false : "Null URL should never match any credentials!";
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentials#matches(java.net.URL)}.
     */
    @Test
    public void testMatchesValidURLOk() throws Exception {
        UrlCredentials creds = new UrlCredentials(AuthType.NONE, new URL("http://localhost:8080/"));
        assert creds.matches(new URL("http://localhost:8080/obr")) : "Base URL should match given URL!";
        assert creds.matches(new URL("http://localhost:8080")) == false : "Base URL shouldn't match given URL!";
        assert creds.matches(new URL("http://localhost:8081/")) == false : "Base URL shouldn't match given URL!";
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

    /**
     * Asserts that two given arrays are equal with respect to their content.
     * 
     * @param expected the expected array;
     * @param given the given array to test.
     */
    private void assertArrayEquals(Object[] expected, Object[] given) {
        assert expected != null && given != null : "Both arrays should never be null!";
        assert expected.length == given.length : "Length mismatch!";
        for (int i = 0; i < expected.length; i++) {
            assert (expected[i] == given[i]) || (expected[i] != null && expected[i].equals(given[i])) : "Elements at index #" + i + " do not match!";
        }
    }
}
