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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.util.Properties;

import org.apache.ace.connectionfactory.impl.UrlCredentialsFactory.MissingValueException;
import org.testng.annotations.Test;

/**
 * Test cases for {@link UrlCredentialsFactory}.
 */
public class UrlCredentialsFactoryTest {
    /** any valid URL will do, no actual connections will be opened to this URL. */
    private static final String AUTH_BASE_URL = "http://localhost/";

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryBasicTypeMissingPasswordFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryClientCertTypeMissingKeystorePasswordFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "client_cert");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_FILE, "bar");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_PASS, "qux");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_FILE, "foo");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryClientCertTypeMissingKeystoreFileFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "client_cert");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_FILE, "bar");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_PASS, "qux");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_PASS, "foo");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT })
    public void testGetCredentialsWithDictionaryClientCertTypeOk() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "client_cert");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_FILE, "foo");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_PASS, "bar");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_FILE, "qux");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_PASS, "quu");

        try {
            UrlCredentialsFactory.getCredentials(props);
        }
        catch (IllegalArgumentException e) {
            // Ok; expected as the implementation tries to open the files "foo" and "qux"...
        }
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryClientCertTypeMissingTruststorePasswordFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "client_cert");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_FILE, "bar");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_PASS, "qux");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_FILE, "foo");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryClientCertTypeMissingTruststoreFileFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "client_cert");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_FILE, "bar");
        props.put(UrlCredentialsFactory.KEY_AUTH_KEYSTORE_PASS, "qux");
        props.put(UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_PASS, "foo");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryBasicTypeMissingUserNameFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT })
    public void testGetCredentialsWithDictionaryBasicTypeOk() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "basic");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_NAME, "foo");
        props.put(UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD, "bar");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testGetCredentialsWithDictionaryInvalidAuthTypeFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "nonsense");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = MissingValueException.class)
    public void testGetCredentialsWithDictionaryMissingBaseUrlFail() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "none");

        UrlCredentialsFactory.getCredentials(props);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testGetCredentialsWithNullDictionaryFail() {
        UrlCredentialsFactory.getCredentials(null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary, java.lang.String)}.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testGetCredentialsWithNullPrefixFail() {
        UrlCredentialsFactory.getCredentials(new Properties(), null);
    }

    /**
     * Test method for {@link org.apache.ace.connectionfactory.impl.UrlCredentialsFactory#getCredentials(java.util.Dictionary)}.
     */
    @Test(groups = { UNIT })
    public void testGetCredentialsWithValidDictionaryOk() {
        Properties props = new Properties();
        props.put(UrlCredentialsFactory.KEY_AUTH_BASE_URL, AUTH_BASE_URL);
        props.put(UrlCredentialsFactory.KEY_AUTH_TYPE, "none");

        UrlCredentialsFactory.getCredentials(props);
    }
}
