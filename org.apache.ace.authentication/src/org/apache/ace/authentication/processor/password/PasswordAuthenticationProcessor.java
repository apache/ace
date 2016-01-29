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

import java.util.Dictionary;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.commons.codec.digest.DigestUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides an {@link AuthenticationProcessor} that implements simple username/password-based
 * authentication and looks up a user in the {@link UserAdmin} service using (by default, can be
 * configured otherwise) the keys "username" and "password". It also supports (MD5, SHA1, SHA256, 
 * SHA384 or SHA512) hashed passwords.
 */
public class PasswordAuthenticationProcessor implements AuthenticationProcessor, ManagedService {

    public static final String PID = "org.apache.ace.authenticationprocessor.password";

    static final String PROPERTY_KEY_USERNAME = "key.username";
    static final String PROPERTY_KEY_PASSWORD = "key.password";
    static final String PROPERTY_PASSWORD_HASHMETHOD = "password.hashmethod";

    private static final String DEFAULT_PROPERTY_KEY_USERNAME = "username";
    private static final String DEFAULT_PROPERTY_KEY_PASSWORD = "password";
    private static final String DEFAULT_PROPERTY_PASSWORD_HASHMETHOD = "none";

    private volatile String m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
    private volatile String m_keyPassword = DEFAULT_PROPERTY_KEY_PASSWORD;
    private volatile String m_passwordHashMethod = DEFAULT_PROPERTY_PASSWORD_HASHMETHOD;

    /**
     * {@inheritDoc}
     */
    public User authenticate(UserAdmin userAdmin, Object... context) {
        final String username = (String) context[0];
        final Object password = context[1];

        if (username == null || "".equals(username.trim())) {
            // Invalid/no username given!
            return null;
        }

        if (password == null) {
            // Invalid/no password given!
            return null;
        }

        User user = userAdmin.getUser(m_keyUsername, username);
        if (user == null || !user.hasCredential(m_keyPassword, hashPassword(password))) {
            // Invalid/unknown user!
            return null;
        }

        return user;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canHandle(Object... context) {
        if (context == null || context.length == 0) {
            throw new IllegalArgumentException("Invalid context!");
        }

        if (context.length != 2) {
            return false;
        }

        if (!(context[0] instanceof String)) {
            return false;
        }

        return ((context[1] instanceof String) || (context[1] instanceof byte[]));
    }

    /**
     * {@inheritDoc}
     */
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String keyUsername = (String) dictionary.get(PROPERTY_KEY_USERNAME);
            if (keyUsername == null || "".equals(keyUsername.trim())) {
                throw new ConfigurationException(PROPERTY_KEY_USERNAME, "Missing property");
            }

            String keyPassword = (String) dictionary.get(PROPERTY_KEY_PASSWORD);
            if (keyPassword == null || "".equals(keyPassword.trim())) {
                throw new ConfigurationException(PROPERTY_KEY_PASSWORD, "Missing property");
            }

            String passwordHashType = (String) dictionary.get(PROPERTY_PASSWORD_HASHMETHOD);
            if (passwordHashType == null || "".equals(passwordHashType.trim())) {
                throw new ConfigurationException(PROPERTY_PASSWORD_HASHMETHOD, "Missing property");
            }
            if (!isValidHashMethod(passwordHashType)) {
                throw new ConfigurationException(PROPERTY_PASSWORD_HASHMETHOD, "Invalid hash method!");
            }

            m_keyUsername = keyUsername;
            m_keyPassword = keyPassword;
            m_passwordHashMethod = passwordHashType;
        }
        else {
            m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
            m_keyPassword = DEFAULT_PROPERTY_KEY_PASSWORD;
            m_passwordHashMethod = DEFAULT_PROPERTY_PASSWORD_HASHMETHOD;
        }
    }

    /**
     * Hashes a given password using the current set hash method.
     * 
     * @param password the password to hash, should not be <code>null</code>.
     * @return the hashed password, never <code>null</code>.
     */
    private Object hashPassword(Object password) {
        if ("none".equalsIgnoreCase(m_passwordHashMethod)) {
            // Very special ROT26 hashing method...
            return password;
        }

        if ("md5".equalsIgnoreCase(m_passwordHashMethod)) {
            if (password instanceof byte[]) {
                return DigestUtils.md5((byte[]) password);
            }
            return DigestUtils.md5((String) password);
        }
        if ("sha1".equalsIgnoreCase(m_passwordHashMethod)) {
            if (password instanceof byte[]) {
                return DigestUtils.sha((byte[]) password);
            }
            return DigestUtils.sha((String) password);
        }
        if ("sha256".equalsIgnoreCase(m_passwordHashMethod)) {
            if (password instanceof byte[]) {
                return DigestUtils.sha256((byte[]) password);
            }
            return DigestUtils.sha256((String) password);
        }
        if ("sha384".equalsIgnoreCase(m_passwordHashMethod)) {
            if (password instanceof byte[]) {
                return DigestUtils.sha384((byte[]) password);
            }
            return DigestUtils.sha384((String) password);
        }
        if ("sha512".equalsIgnoreCase(m_passwordHashMethod)) {
            if (password instanceof byte[]) {
                return DigestUtils.sha512((byte[]) password);
            }
            return DigestUtils.sha512((String) password);
        }
        return password;
    }

    /**
     * Determines whether the given hash method is valid.
     * 
     * @param hashMethod the hash method to test, can be <code>null</code> or empty.
     * @return <code>true</code> if the given hash method is valid/supported, <code>false</code> otherwise.
     */
    private boolean isValidHashMethod(String hashMethod) {
// @formatter:off
      return "none".equalsIgnoreCase(hashMethod) 
          || "md5".equalsIgnoreCase(hashMethod) 
          || "sha1".equalsIgnoreCase(hashMethod) 
          || "sha256".equalsIgnoreCase(hashMethod) 
          || "sha384".equalsIgnoreCase(hashMethod) 
          || "sha512".equalsIgnoreCase(hashMethod);
// @formatter:on
    }
}
