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
package org.apache.ace.authenticationprocessor.password;

import static org.apache.ace.authenticationprocessor.password.PasswordAuthenticationProcessor.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Test cases for {@link PasswordAuthenticationProcessor}.
 */
public class PasswordAuthenticationProcessorTest {
    
    private UserAdmin m_userAdmin;

    @Before
    public void setUp() {
        m_userAdmin = mock(UserAdmin.class);
    }

    /**
     * Tests that authenticating with a empty username will yield null.
     */
    @Test
    public void testAuthenticateEmptyUserNameYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "", "secret");
        assertNull(result);
    }

    /**
     * Tests that authenticating a known user with an invalid password will yield null.
     */
    @Test
    public void testAuthenticateKnownUserWithInvalidPasswordYieldsNull() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("otherSecret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", "secret");
        assertNull(result);
    }

    /**
     * Tests that authenticating a known user with a correct password will not yield null.
     */
    @Test
    public void testAuthenticateKnownUserYieldsValidResult() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("secret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", "secret");
        assertNotNull(result);
        
        assertEquals("bob", user.getName());
    }

    /**
     * Tests that authenticating with a null password will yield null.
     */
    @Test
    public void testAuthenticateNullPasswordYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", null);
        assertNull(result);
    }

    /**
     * Tests that authenticating with a null username will yield null.
     */
    @Test
    public void testAuthenticateNullUserNameYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, null, "secret");
        assertNull(result);
    }

    /**
     * Tests that a class cast exception is thrown for invalid context when calling authenticate.
     */
    @Test(expected = ClassCastException.class)
    public void testAuthenticateThrowsClassCastForInvalidContext() {
        new PasswordAuthenticationProcessor().authenticate(m_userAdmin, new Object(), "foo");
    }

    /**
     * Tests that authenticating an unknown user will yield null.
     */
    @Test
    public void testAuthenticateUnknownUserYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", "secret");
        assertNull(result);
    }

    /**
     * Tests that canHandle yields true for string and byte array.
     */
    @Test
    public void testCanHandleDoesAcceptStringAndByteArray() {
        assertTrue(new PasswordAuthenticationProcessor().canHandle("foo", "bar".getBytes()));
    }

    /**
     * Tests that canHandle yields true for two strings.
     */
    @Test
    public void testCanHandleDoesAcceptTwoStrings() {
        assertTrue(new PasswordAuthenticationProcessor().canHandle("foo", "bar"));
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for an empty context.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptEmptyArray() {
        new PasswordAuthenticationProcessor().canHandle(new Object[0]);
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for a null context.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptNull() {
        new PasswordAuthenticationProcessor().canHandle((Object[]) null);
    }

    /**
     * Tests that canHandle yields false for too few arguments. 
     */
    @Test
    public void testCanHandleDoesNotAcceptSingleArgument() {
        assertFalse(new PasswordAuthenticationProcessor().canHandle(new Object()));
    }
    
    /**
     * Tests that canHandle yields false for a string and other object. 
     */
    @Test
    public void testCanHandleDoesNotAcceptStringAndOtherObject() {
        assertFalse(new PasswordAuthenticationProcessor().canHandle("foo", new Object()));
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test
    public void testCanHandleDoesNotAcceptWrongTypes() {
        assertFalse(new PasswordAuthenticationProcessor().canHandle(new Object(), new Object()));
    }
    
    /**
     * Tests that updated does not throw an exception for a correct configuration.
     */
    @Test
    public void testUpdatedDoesAcceptCorrectProperties() throws ConfigurationException {
        final String keyUsername = "foo";
        final String keyPassword = "bar";
        
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, keyUsername);
        props.put(PROPERTY_KEY_PASSWORD, keyPassword);
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "sha1");

        PasswordAuthenticationProcessor processor = new PasswordAuthenticationProcessor();

        processor.updated(props);

        byte[] hashedPw = DigestUtils.sha("secret");
        
        // Test whether we can use the new properties...
        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq(keyPassword), eq(hashedPw))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq(keyUsername), eq("bob"))).thenReturn(user);

        User result = processor.authenticate(m_userAdmin, "bob", "secret");
        assertNotNull(result);
        
        assertEquals("bob", user.getName());
    }
    
    /**
     * Tests that updated throws an exception for missing "key.password" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyPassword() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PASSWORD, "");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");
        
        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "key.username" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyUsername() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "");
        props.put(PROPERTY_KEY_PASSWORD, "foo");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");
        
        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "password.hashtype" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyPasswordHashType() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PASSWORD, "bar");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "");
        
        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "key.password" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyPassword() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");

        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "key.username" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyUsername() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_PASSWORD, "foo");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");

        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "password.hashtype" property. 
     */
    @Test(expected = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingPasswordHashType() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PASSWORD, "foo");

        new PasswordAuthenticationProcessor().updated(props);
    }
}
