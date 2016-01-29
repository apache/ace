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
package org.apache.ace.authentication.processor.clientcert;

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
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides an {@link AuthenticationProcessor} that implements authentication based on certificates 
 * and looks up a user in the {@link UserAdmin} service using (by default, can be configured 
 * otherwise) the key "username". If a matching user is found, it is considered authenticated.
 */
public class ClientCertAuthenticationProcessor implements AuthenticationProcessor, ManagedService {

    public static final String PID = "org.apache.ace.authenticationprocessor.clientcert";

    static final String ATTRIBUTE_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";
    static final String ATTRIBUTE_CIPHER_SUITE = "javax.servlet.request.cipher_suite";

    static final String PROPERTY_USERNAME_LOOKUPKEY = "user.name.lookupKey";
    static final String PROPERTY_USERNAME_MATCH_POLICY = "user.name.matchPolicy";
    static final String PROPERTY_VERIFY_CERT_VALIDITY = "certificate.verifyValidity";

    private static final String DEFAULT_PROPERTY_USERNAME_LOOKUPKEY = "username";
    private static final String DEFAULT_PROPERTY_USERNAME_MATCHPOLICY = "cn";
    private static final boolean DEFAULT_PROPERTY_VERIFY_CERT_VALIDITY = true;

    private volatile String m_nameLookupKey = DEFAULT_PROPERTY_USERNAME_LOOKUPKEY;
    private volatile String m_nameMatchPolicy = DEFAULT_PROPERTY_USERNAME_MATCHPOLICY;
    private volatile boolean m_verifyCertValidity = DEFAULT_PROPERTY_VERIFY_CERT_VALIDITY;
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

        if (!(context[0] instanceof HttpServletRequest)) {
            return false;
        }
        
        final HttpServletRequest request = (HttpServletRequest) context[0];
        return (request.getAttribute(ATTRIBUTE_CIPHER_SUITE) != null) && (request.getAttribute(ATTRIBUTE_X509_CERTIFICATE) != null);
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

        String name = getName(cert);
        if (name == null) {
            // No common name given; cannot retrieve user credentials...
            m_log.log(LogService.LOG_DEBUG, "Failed to obtain common name of X509 certificate!");
            return null;
        }
        
        User user = getUser(userAdmin, name);
        if (user == null) {
            // Invalid/unknown user!
            m_log.log(LogService.LOG_DEBUG, "Failed to validate user using certificate!");
            return null;
        }

        return user;
    }

    /**
     * {@inheritDoc}
     */
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String usernameLookupKey = (String) dictionary.get(PROPERTY_USERNAME_LOOKUPKEY);
            if (usernameLookupKey == null || "".equals(usernameLookupKey.trim())) {
                throw new ConfigurationException(PROPERTY_USERNAME_LOOKUPKEY, "Missing property");
            }

            String usernameMatchPolicy = (String) dictionary.get(PROPERTY_USERNAME_MATCH_POLICY);
            if (usernameMatchPolicy == null || "".equals(usernameMatchPolicy.trim())) {
                throw new ConfigurationException(PROPERTY_USERNAME_MATCH_POLICY, "Missing property");
            }
            
            Object verifyCertValidity = dictionary.get(PROPERTY_VERIFY_CERT_VALIDITY);
            if (verifyCertValidity == null || !("true".equals(verifyCertValidity) || "false".equals(verifyCertValidity))) {
                throw new ConfigurationException(PROPERTY_VERIFY_CERT_VALIDITY, "Missing or invalid property!");
            }

            m_nameLookupKey = usernameLookupKey;
            m_nameMatchPolicy = usernameMatchPolicy;
            m_verifyCertValidity = Boolean.parseBoolean((String) verifyCertValidity);
        }
        else {
            m_nameLookupKey = DEFAULT_PROPERTY_USERNAME_LOOKUPKEY;
            m_nameMatchPolicy = DEFAULT_PROPERTY_USERNAME_MATCHPOLICY;
            m_verifyCertValidity = DEFAULT_PROPERTY_VERIFY_CERT_VALIDITY;
        }
    }

    /**
     * Retrieves the name for the given certificate.
     * 
     * @param certificate the certificate to get its name for, cannot be <code>null</code>.
     * @return the name for the given certificate, can be <code>null</code>.
     */
    private String getName(X509Certificate certificate) {
        try {
            String dn = certificate.getSubjectX500Principal().getName();
            if ("dn".equalsIgnoreCase(m_nameMatchPolicy)) {
                return dn;
            }

            LdapName ldapDN = new LdapName(dn);
            for (Rdn rdn : ldapDN.getRdns()) {
                if (m_nameMatchPolicy.equalsIgnoreCase(rdn.getType())) {
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
        if (m_nameLookupKey != null) {
            user = userAdmin.getUser(m_nameLookupKey, name);
        }
        if (user == null) {
            user = userAdmin.getRole(name);
        }
        return (user instanceof User) ? (User) user : null;
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
                if (m_verifyCertValidity) {
                    cert.checkValidity();
                }
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
