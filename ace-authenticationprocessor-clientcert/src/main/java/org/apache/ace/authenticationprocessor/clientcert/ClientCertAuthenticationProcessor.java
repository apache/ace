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
package org.apache.ace.authenticationprocessor.clientcert;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Dictionary;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.servlet.http.HttpServletRequest;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides an {@link AuthenticationProcessor} that implements basic HTTP authentication and looks
 * up a user in the {@link UserAdmin} service using (by default, can be configured otherwise) the
 * keys "username" and "publickey". Only if the public key of the user in UserAdmin equals to the
 * public key of the obtained certificate, the user is considered authenticated.
 */
public class ClientCertAuthenticationProcessor implements AuthenticationProcessor, ManagedService {

    public static final String PID = "org.apache.ace.authenticationprocessor.clientcert";

    static final String ATTRIBUTE_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";
    static final String ATTRIBUTE_CIPHER_SUITE = "javax.servlet.request.cipher_suite";

    static final String PROPERTY_KEY_USERNAME = "key.username";
    static final String PROPERTY_KEY_PUBLICKEY = "key.publickey";

    private static final String DEFAULT_PROPERTY_KEY_USERNAME = "username";
    private static final String DEFAULT_PROPERTY_KEY_PUBLICKEY = "publickey";

    private volatile String m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
    private volatile String m_keyPublicKey = DEFAULT_PROPERTY_KEY_PUBLICKEY;
    private volatile LogService m_log;

    /**
     * Creates a new {@link ClientCertAuthenticationProcessor} instance.
     */
    public ClientCertAuthenticationProcessor() {
        // Nop
    }

    /**
     * Creates a new {@link ClientCertAuthenticationProcessor} with a given logger.
     */
    ClientCertAuthenticationProcessor(LogService log) {
        m_log = log;
    }

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

        String cipherSuite = (String) request.getAttribute(ATTRIBUTE_CIPHER_SUITE);
        if (cipherSuite == null) {
            // No SSL connection?!
            m_log.log(LogService.LOG_DEBUG, "No SSL connection (no cipher suite found)?!");
            return null;
        }

        X509Certificate certificateChain[] = (X509Certificate[]) request.getAttribute(ATTRIBUTE_X509_CERTIFICATE);
        if (certificateChain == null || certificateChain.length == 0) {
            // No certificates given...
            m_log.log(LogService.LOG_DEBUG, "Failed to obtain X509 certificate chain from request!");
            return null;
        }

        // Validate the certificate chain...
        // TODO there should be more checks performed here...
        final X509Certificate cert = validateCertificateChain(certificateChain);
        if (cert == null) {
            // Invalid certificate(chain)...
            m_log.log(LogService.LOG_DEBUG, "Failed to validate X509 certificate chain!");
            return null;
        }

        String username = getCommonName(cert);
        if (username == null) {
            // No common name given; cannot retrieve user credentials...
            m_log.log(LogService.LOG_DEBUG, "Failed to obtain common name of X509 certificate!");
            return null;
        }

        User user = userAdmin.getUser(m_keyUsername, username);
        if (user == null || !user.hasCredential(m_keyPublicKey, cert.getPublicKey().getEncoded())) {
            // Invalid/unknown user!
            m_log.log(LogService.LOG_DEBUG, "Failed to validate user using certificate!");
            return null;
        }

        return user;
    }

    /**
     * {@inheritDoc}
     */
    public void updated(Dictionary dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String keyUsername = (String) dictionary.get(PROPERTY_KEY_USERNAME);
            if (keyUsername == null || "".equals(keyUsername.trim())) {
                throw new ConfigurationException(PROPERTY_KEY_USERNAME, "Missing property");
            }

            String keyPassword = (String) dictionary.get(PROPERTY_KEY_PUBLICKEY);
            if (keyPassword == null || "".equals(keyPassword.trim())) {
                throw new ConfigurationException(PROPERTY_KEY_PUBLICKEY, "Missing property");
            }

            m_keyUsername = keyUsername;
            m_keyPublicKey = keyPassword;
        }
        else {
            m_keyUsername = DEFAULT_PROPERTY_KEY_USERNAME;
            m_keyPublicKey = DEFAULT_PROPERTY_KEY_PUBLICKEY;
        }
    }

    /**
     * Retrieves the common name for the given certificate.
     * 
     * @param certificate the certificate to get its common name for, cannot be <code>null</code>.
     * @return the common name for the given certificate, can be <code>null</code>.
     */
    private String getCommonName(X509Certificate certificate) {
        try {
            String dn = certificate.getSubjectX500Principal().getName();

            LdapName ldapDN = new LdapName(dn);
            for (Rdn rdn : ldapDN.getRdns()) {
                if ("CN".equals(rdn.getType())) {
                    return (String) rdn.getValue();
                }
            }
        }
        catch (InvalidNameException e) {
            // Ignore...
        }
        return null;
    }

    /**
     * Validates the certificate chain whether all certificates are valid and not expired.
     * 
     * @param certificateChain the chain of certificates to validate, cannot be <code>null</code>.
     * @return if the chain is valid, the first certificate, <code>null</code> otherwise.
     */
    private X509Certificate validateCertificateChain(X509Certificate[] certificateChain) {
        try {
            for (X509Certificate cert : certificateChain) {
                if (cert == null) {
                    // Bogus certificate given...
                    return null;
                }
                cert.checkValidity();
            }
        }
        catch (CertificateExpiredException e) {
            // Refuse to go further with expired certificates...
            m_log.log(LogService.LOG_DEBUG, "Certificate expired!", e);
            return null;
        }
        catch (CertificateNotYetValidException e) {
            // Refuse to go further with invalid certificates...
            m_log.log(LogService.LOG_DEBUG, "Certificate not yet valid!", e);
            return null;
        }

        // This *might* be a valid certificate chain; return the first certificate...
        return certificateChain[0];
    }
}
