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

import java.util.Properties;

import org.apache.ace.connectionfactory.impl.UrlCredentialsFactory.MissingValueException;
import org.junit.Test;

/**
 * Test cases for {@link UrlCredentialsFactory}.
 */
public class UrlCredentialsFactoryTest {

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(expected = MissingValueException.class)
    public void testGetCredentialsWithDictionaryBasicTypeMissingPasswordFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, "http://localhost:8080/");
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(expected = MissingValueException.class)
    public void testGetCredentialsWithDictionaryBasicTypeMissingUserNameFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, "http://localhost:8080/");
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test
    public void testGetCredentialsWithDictionaryBasicTypeOk() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, "http://localhost:8080/");
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "foo");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetCredentialsWithDictionaryInvalidAuthTypeFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, "http://localhost:8080/");
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "nonsense");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(expected = MissingValueException.class)
    public void testGetCredentialsWithDictionaryMissingBaseUrlFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "none");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetCredentialsWithNullDictionaryFail() {
        UrlCredentialsFactory.getCredentials(null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary, java.lang.String)}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetCredentialsWithNullPrefixFail() {
        UrlCredentialsFactory.getCredentials(new Properties(), null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test
    public void testGetCredentialsWithValidDictionaryOk() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, "http://localhost:8080/");
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "none");

        UrlCredentialsFactory.getCredentials(props);
    }
}
