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
package org.apache.ace.authentication.processor.basicauth;

import static org.apache.ace.authentication.processor.basicauth.BasicHttpAuthenticationProcessor.AUTHORIZATION_HEADER;
import static org.apache.ace.authentication.processor.basicauth.BasicHttpAuthenticationProcessor.PROPERTY_KEY_PASSWORD;
import static org.apache.ace.authentication.processor.basicauth.BasicHttpAuthenticationProcessor.PROPERTY_KEY_USERNAME;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link BasicHttpAuthenticationProcessor}.
 */
public class BasicHttpAuthenticationProcessorTest {

    private UserAdmin m_userAdmin;
    private HttpServletRequest m_servletRequest;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_userAdmin = mock(UserAdmin.class);
        m_servletRequest = mock(HttpServletRequest.class);
    }

    /**
     * Tests that a null authentication header will yield null.
     */
    @Test()
    public void testAuthenticateEmptyAuthenticationHeaderYieldsNull() {
        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Expected no result!");
    }

    /**
     * Tests that an invalid authentication header will yield null.
     */
    @Test()
    public void testAuthenticateInvalidAuthenticationHeaderYieldsNull() {
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(createAuthHeaderValue("bob"));

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("secret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Expected no result!");
    }

    /**
     * Tests that a known user with an invalid password will yield null.
     */
    @Test()
    public void testAuthenticateKnownUserWithInvalidPasswordYieldsNull() {
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(createAuthHeaderValue("bob:secret"));

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("otherSecret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Expected no result!");
    }

    /**
     * Tests that a known user will not yield null.
     */
    @Test()
    public void testAuthenticateKnownUserYieldsValidResult() {
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(createAuthHeaderValue("bob:secret"));

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("secret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNotNull(result, "Expected a valid user to be returned!");

        assertEquals(user.getName(), "bob", "Expected user bob to be returned!");
    }

    /**
     * Tests that a non Base64 authentication header will yield null.
     */
    @Test()
    public void testAuthenticateNonBase64AuthenticationHeaderYieldsNull() {
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn("foo");

        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Expected no result!");
    }

    /**
     * Tests that a class cast exception is thrown for invalid context when calling authenticate.
     */
    @Test(expectedExceptions = ClassCastException.class)
    public void testAuthenticateThrowsClassCastForInvalidContext() {
        new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, new Object());
    }

    /**
     * Tests that an unknown user will yield null.
     */
    @Test()
    public void testAuthenticateUnknownUserYieldsNull() {
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(createAuthHeaderValue("alice:secret"));

        User result = new BasicHttpAuthenticationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Expected no result!");
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test()
    public void testCanHandleDoesAcceptServletRequest() {
        assertTrue(new BasicHttpAuthenticationProcessor().canHandle(mock(HttpServletRequest.class)));
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for an empty context.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptEmptyArray() {
        new BasicHttpAuthenticationProcessor().canHandle(new Object[0]);
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for a null context.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptNull() {
        new BasicHttpAuthenticationProcessor().canHandle((Object[]) null);
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test()
    public void testCanHandleDoesNotAcceptUnhandledContext() {
        assertFalse(new BasicHttpAuthenticationProcessor().canHandle(new Object()));
    }

    /**
     * Tests that updated throws an exception for missing "key.username" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyUsername() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(PROPERTY_KEY_USERNAME, "");
        props.put(PROPERTY_KEY_PASSWORD, "foo");

        new BasicHttpAuthenticationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.username" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyUsername() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(PROPERTY_KEY_PASSWORD, "foo");

        new BasicHttpAuthenticationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.password" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyPassword() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(PROPERTY_KEY_USERNAME, "foo");

        new BasicHttpAuthenticationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.password" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyPassword() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PASSWORD, "");

        new BasicHttpAuthenticationProcessor().updated(props);
    }

    /**
     * Tests that updated does not throw an exception for a correct configuration.
     */
    @Test()
    public void testUpdatedDoesAcceptCorrectProperties() throws ConfigurationException {
        final String keyUsername = "foo";
        final String keyPassword = "bar";

        Dictionary<String, Object> props = new Hashtable<>();
        props.put(PROPERTY_KEY_USERNAME, keyUsername);
        props.put(PROPERTY_KEY_PASSWORD, keyPassword);

        BasicHttpAuthenticationProcessor processor = new BasicHttpAuthenticationProcessor();

        processor.updated(props);

        // Test whether we can use the new properties...
        when(m_servletRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(createAuthHeaderValue("bob:secret"));

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq(keyPassword), eq("secret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq(keyUsername), eq("bob"))).thenReturn(user);

        User result = processor.authenticate(m_userAdmin, m_servletRequest);
        assertNotNull(result, "Expected a valid user to be returned!");

        assertEquals(user.getName(), "bob", "Expected user bob to be returned!");
    }

    /**
     * @return the basic authentication header, never <code>null</code>.
     */
    private String createAuthHeaderValue(String credentials) {
        return "Basic " + new Base64().encodeToString(credentials.getBytes());
    }
}
