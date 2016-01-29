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

import java.io.UnsupportedEncodingException;
import java.util.Dictionary;

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.commons.codec.binary.Base64;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides an {@link AuthenticationProcessor} that implements basic HTTP authentication and looks
 * up a user in the {@link UserAdmin} service using (by default, can be configured otherwise) the
 * keys "username" and "password".
 */
public class BasicHttpAuthenticationProcessor implements AuthenticationProcessor, ManagedService {

    public static final String PID = "org.apache.ace.authenticationprocessor.basicauth";

    /** The name of the HTTP-header used for HTTP authentication. */
    static final String AUTHORIZATION_HEADER = "Authorization";

    static final String PROPERTY_KEY_USERNAME = "key.username";
    static final String PROPERTY_KEY_PASSWORD = "key.password";

    private static final String DEFAULT_PROPERTY_KEY_USERNAME = "username";
    private static final String DEFAULT_PROPERTY_KEY_PASSWORD = "password";

    private volatile String m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
    private volatile String m_keyPassword = DEFAULT_PROPERTY_KEY_PASSWORD;

    /**
     * {@inheritDoc}
     */
    public boolean canHandle(Object... context) {
        if (context == null || context.length == 0) {
            throw new IllegalArgumentException("Invalid context!");
        }

        return (context[0] instanceof HttpServletRequest);
    }

    /**
     * {@inheritDoc}
     */
    public User authenticate(UserAdmin userAdmin, Object... context) {
        final HttpServletRequest request = (HttpServletRequest) context[0];

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.length() < 6) {
            // No authorization header obtained; cannot authorize...
            return null;
        }

        // Form = 'Basic ' + base64 encoded credentials
        String packedCredentials = decodeBase64(header);
        if (packedCredentials == null) {
            // No credentials obtained; cannot authenticate...
            return null;
        }

        // Form = <user>:<password>
        String[] credentials = packedCredentials.split(":");
        if (credentials.length != 2) {
            // A colon should always be present!
            return null;
        }

        User user = getUser(userAdmin, credentials[0]);
        if (user == null || !user.hasCredential(m_keyPassword, credentials[1])) {
            // Invalid/unknown user!
            return null;
        }

        return user;
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

            m_keyUsername = keyUsername;
            m_keyPassword = keyPassword;
        }
        else {
            m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
            m_keyPassword = DEFAULT_PROPERTY_KEY_PASSWORD;
        }
    }

    /**
     * Decodes a given base64-encoded string.
     * 
     * @param header the base64 encoded header to decode.
     * @return the base64 decoded string, can be <code>null</code>.
     */
    private String decodeBase64(String header) {
        byte[] array = Base64.decodeBase64(header.substring(6));
        if (array == null) {
            return null;
        }

        try {
            return new String(array, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // Should never occur, as Java is always capable of handling UTF-8!
            throw new RuntimeException(e);
        }
    }

    /**
     * Searches for a user with a given name.
     * <p>
     * This method first looks whether there's a user with the property 
     * "m_keyUsername" that matches the given username, if not found, it will 
     * try to retrieve a role with the given name.
     * </p>
     * 
     * @param userAdmin the {@link UserAdmin} service to get users from;
     * @param name the name of the user to retrieve.
     * @return a {@link User}, can be <code>null</code> if no such user is found.
     */
    private User getUser(UserAdmin userAdmin, String name) {
        Role user = null;
        if (m_keyUsername != null) {
            user = userAdmin.getUser(m_keyUsername, name);
        }
        if (user == null) {
            user = userAdmin.getRole(name);
        }
        return (user instanceof User) ? (User) user : null;
    }
}
