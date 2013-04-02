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
package org.apache.ace.authentication.processor.password;

import static org.apache.ace.authentication.processor.password.PasswordAuthenticationProcessor.PROPERTY_KEY_PASSWORD;
import static org.apache.ace.authentication.processor.password.PasswordAuthenticationProcessor.PROPERTY_KEY_USERNAME;
import static org.apache.ace.authentication.processor.password.PasswordAuthenticationProcessor.PROPERTY_PASSWORD_HASHMETHOD;
import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.apache.ace.authentication.processor.password.PasswordAuthenticationProcessor;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.Mockito;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link PasswordAuthenticationProcessor}.
 */
public class PasswordAuthenticationProcessorTest {
    
    private UserAdmin m_userAdmin;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_userAdmin = mock(UserAdmin.class);
        Mockito.reset(m_userAdmin);
    }

    /**
     * Tests that authenticating with a empty username will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateEmptyUserNameYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "", "secret");
        assert result == null : "Expected no valid user to be returned!";
    }

    /**
     * Tests that authenticating a known user with an invalid password will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateKnownUserWithInvalidPasswordYieldsNull() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("otherSecret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", "secret");
        assert result == null : "Expected no valid user to be returned!";
    }

    /**
     * Tests that authenticating a known user with a correct password will not yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateKnownUserYieldsValidResult() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("password"), eq("secret"))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", "secret");
        assert result != null : "Expected a valid user to be returned!";
        
        assert "bob".equals(user.getName()) : "Expected bob to be returned!";
    }

    /**
     * Tests that authenticating with a null password will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateNullPasswordYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "bob", null);
        assert result == null : "Expected no valid user to be returned!";
    }

    /**
     * Tests that authenticating with a null username will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateNullUserNameYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, null, "secret");
        assert result == null : "Expected no valid user to be returned!";
    }

    /**
     * Tests that a class cast exception is thrown for invalid context when calling authenticate.
     */
    @Test(groups = { UNIT }, expectedExceptions = ClassCastException.class)
    public void testAuthenticateThrowsClassCastForInvalidContext() {
        new PasswordAuthenticationProcessor().authenticate(m_userAdmin, new Object(), "foo");
    }

    /**
     * Tests that authenticating an unknown user will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateUnknownUserYieldsNull() {
        User result = new PasswordAuthenticationProcessor().authenticate(m_userAdmin, "alice", "secret");
        assert result == null : "Expected no valid user to be returned!";
    }

    /**
     * Tests that canHandle yields true for string and byte array.
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesAcceptStringAndByteArray() {
        assert new PasswordAuthenticationProcessor().canHandle("foo", "bar".getBytes()) : "Expected the processor to handle a byte array!";
    }

    /**
     * Tests that canHandle yields true for two strings.
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesAcceptTwoStrings() {
        assert new PasswordAuthenticationProcessor().canHandle("foo", "bar") : "Expected the processor to handle a string!";
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for an empty context.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptEmptyArray() {
        new PasswordAuthenticationProcessor().canHandle(new Object[0]);
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for a null context.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptNull() {
        new PasswordAuthenticationProcessor().canHandle((Object[]) null);
    }

    /**
     * Tests that canHandle yields false for too few arguments. 
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesNotAcceptSingleArgument() {
        assert new PasswordAuthenticationProcessor().canHandle(new Object()) == false : "Expected the processor to NOT handle any object!";
    }
    
    /**
     * Tests that canHandle yields false for a string and other object. 
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesNotAcceptStringAndOtherObject() {
        assert new PasswordAuthenticationProcessor().canHandle("foo", new Object()) == false : "Expected the processor to NOT handle any object!";
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesNotAcceptWrongTypes() {
        assert new PasswordAuthenticationProcessor().canHandle(new Object(), new Object()) == false : "Expected the processor to NOT handle any object!";
    }
    
    /**
     * Tests that updated does not throw an exception for a correct configuration.
     */
    @Test(groups = { UNIT })
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
        assert result != null : "Expected a valid user to be returned!";
        
        assert "bob".equals(user.getName()) : "Expected bob to be returned!";
    }
    
    /**
     * Tests that updated throws an exception for missing "key.password" property. 
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
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
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
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
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
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
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyPassword() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");

        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "key.username" property. 
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyUsername() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_PASSWORD, "foo");
        props.put(PROPERTY_PASSWORD_HASHMETHOD, "none");

        new PasswordAuthenticationProcessor().updated(props);
    }
    
    /**
     * Tests that updated throws an exception for missing "password.hashtype" property. 
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingPasswordHashType() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PASSWORD, "foo");

        new PasswordAuthenticationProcessor().updated(props);
    }
}
