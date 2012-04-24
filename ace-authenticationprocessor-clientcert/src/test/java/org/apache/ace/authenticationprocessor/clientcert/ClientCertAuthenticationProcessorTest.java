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

import static org.apache.ace.authenticationprocessor.clientcert.ClientCertAuthenticationProcessor.ATTRIBUTE_CIPHER_SUITE;
import static org.apache.ace.authenticationprocessor.clientcert.ClientCertAuthenticationProcessor.ATTRIBUTE_X509_CERTIFICATE;
import static org.apache.ace.authenticationprocessor.clientcert.ClientCertAuthenticationProcessor.PROPERTY_KEY_PUBLICKEY;
import static org.apache.ace.authenticationprocessor.clientcert.ClientCertAuthenticationProcessor.PROPERTY_KEY_USERNAME;
import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.mockito.Mockito;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link ClientCertAuthenticationProcessor}.
 */
public class ClientCertAuthenticationProcessorTest {

    private static MemoryKeyStore m_keystore;

    private LogService m_log;
    private UserAdmin m_userAdmin;
    private HttpServletRequest m_servletRequest;

    /**
     * @return the day after tomorrow, never <code>null</code>.
     */
    private static Date dayAfterTomorrow() {
        Calendar cal = getToday();
        cal.add(Calendar.DAY_OF_MONTH, +2);
        return cal.getTime();
    }

    /**
     * @return the day before yesterday, never <code>null</code>.
     */
    private static Date dayBeforeYesterday() {
        Calendar cal = getToday();
        cal.add(Calendar.DAY_OF_MONTH, -2);
        return cal.getTime();
    }

    /**
     * @return today as date, without time component, never <code>null</code>.
     */
    private static Calendar getToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /**
     * @return the date of tomorrow, never <code>null</code>.
     */
    private static Date tomorrow() {
        Calendar cal = getToday();
        cal.add(Calendar.DAY_OF_MONTH, +1);
        return cal.getTime();
    }

    /**
     * @return the date of yesterday, never <code>null</code>.
     */
    private static Date yesterday() {
        Calendar cal = getToday();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return cal.getTime();
    }

    /**
     * Creates an in-memory keystore for this test case.
     */
    @BeforeClass(alwaysRun = true)
    public static void init() {
        m_keystore = new MemoryKeyStore("cn=testCA", dayBeforeYesterday(), dayAfterTomorrow());
    }

    /**
     * Set up for each individual test.
     */
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_log = mock(LogService.class);

        m_userAdmin = mock(UserAdmin.class);
        m_servletRequest = mock(HttpServletRequest.class);

        when(m_servletRequest.getAuthType()).thenReturn(HttpServletRequest.CLIENT_CERT_AUTH);
        when(m_servletRequest.getAttribute(ATTRIBUTE_CIPHER_SUITE)).thenReturn("bogus-cipher-suite");
    }

    /**
     * Tests that a null certificate chain will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateNoCertificateChainYieldsNull() {
        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that an empty certificate chain will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateEmptyCertificateChainYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(new X509Certificate[0]);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that authenticating a known user with an invalid (expired) certificate will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateKnownUserWithExpiredCertificateYieldsNull() {
        X509Certificate[] certificateChain = createExpiredCertificateChain("bob");
        PublicKey publickey = certificateChain[0].getPublicKey();

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certificateChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("publickey"), eq(publickey.getEncoded()))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that authenticating a known user with an invalid (not valid) certificate will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateKnownUserWithNotValidCertificateYieldsNull() {
        X509Certificate[] certificateChain = createExpiredCertificateChain("bob");
        PublicKey publickey = certificateChain[0].getPublicKey();

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(
            createNotValidCertificateChain("bob"));

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("publickey"), eq(publickey.getEncoded()))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that authenticating a known user with a valid certificate will not yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateKnownUserYieldsValidResult() {
        X509Certificate[] certChain = createValidCertificateChain("bob");
        PublicKey publicKey = certChain[0].getPublicKey();

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("publickey"), eq(publicKey.getEncoded()))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result != null : "Expected a valid user to be returned!";

        assert "bob".equals(user.getName()) : "Expected bob to be returned as user!";
    }

    /**
     * Tests that a missing cipher suite header will the authenticate method to yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateMissingCipherSuiteHeaderYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_CIPHER_SUITE)).thenReturn(null);
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(createValidCertificateChain("bob"));

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that a class cast exception is thrown for invalid context when calling authenticate.
     */
    @Test(groups = { UNIT }, expectedExceptions = ClassCastException.class)
    public void testAuthenticateThrowsClassCastForInvalidContext() {
        createAuthorizationProcessor().authenticate(m_userAdmin, new Object());
    }

    /**
     * Tests that an unknown user will yield null.
     */
    @Test(groups = { UNIT })
    public void testAuthenticateUnknownUserYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(createValidCertificateChain("bob"));

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assert result == null : "Did not expect a valid user to be returned!";
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesAcceptServletRequest() {
        assert createAuthorizationProcessor().canHandle(mock(HttpServletRequest.class));
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for an empty context.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptEmptyArray() {
        createAuthorizationProcessor().canHandle(new Object[0]);
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for a null context.
     */
    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptNull() {
        createAuthorizationProcessor().canHandle((Object[]) null);
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test(groups = { UNIT })
    public void testCanHandleDoesNotAcceptUnhandledContext() {
        assert createAuthorizationProcessor().canHandle(new Object()) == false;
    }

    /**
     * Tests that updated does not throw an exception for a correct configuration.
     */
    @Test(groups = { UNIT })
    public void testUpdatedDoesAcceptCorrectProperties() throws ConfigurationException {
        final String keyUsername = "foo";
        final String keyPublicKey = "bar";
        
        Mockito.reset(m_userAdmin, m_servletRequest);

        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, keyUsername);
        props.put(PROPERTY_KEY_PUBLICKEY, keyPublicKey);

        ClientCertAuthenticationProcessor processor = createAuthorizationProcessor();

        processor.updated(props);

        X509Certificate[] certificateChain = createValidCertificateChain("alice");
        PublicKey publickey = certificateChain[0].getPublicKey();

        // Test whether we can use the new properties...
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certificateChain);
        when(m_servletRequest.getAttribute(ATTRIBUTE_CIPHER_SUITE)).thenReturn("bogus-cipher-suite");

        User user = mock(User.class);
        when(user.getName()).thenReturn("alice");
        when(user.hasCredential(eq(keyPublicKey), eq(publickey.getEncoded()))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq(keyUsername), eq("alice"))).thenReturn(user);

        User result = processor.authenticate(m_userAdmin, m_servletRequest);
        assert result != null : "Expected a valid user to be returned!";

        assert "alice".equals(user.getName()) : "Expected alice to be returned as user!";
    }

    /**
     * Tests that updated throws an exception for missing "key.password" property.
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyPassword() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");
        props.put(PROPERTY_KEY_PUBLICKEY, "");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.username" property.
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyKeyUsername() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "");
        props.put(PROPERTY_KEY_PUBLICKEY, "foo");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.password" property.
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyPassword() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_USERNAME, "foo");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "key.username" property.
     */
    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingKeyUsername() throws ConfigurationException {
        Properties props = new Properties();
        props.put(PROPERTY_KEY_PUBLICKEY, "foo");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Creates a new {@link ClientCertAuthenticationProcessor} instance.
     * 
     * @return a new authentication processor instance, never <code>null</code>.
     */
    private ClientCertAuthenticationProcessor createAuthorizationProcessor() {
        return new ClientCertAuthenticationProcessor(m_log);
    }

    /**
     * Creates a new certificate.
     * 
     * @param name the (common) name of the certificate;
     * @param notBefore the date after which the certificate is valid;
     * @param notAfter the date until the certificate is valid.
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate createCertificate(String name, final Date notBefore, final Date notAfter) {
        KeyPair keypair = m_keystore.generateKeyPair();
        return m_keystore.createCertificate("alias", "cn=" + name, notBefore, notAfter, keypair.getPublic());
    }

    /**
     * Creates a new (valid) certificate valid from yesterday until tomorrow.
     * 
     * @param name the (common) name of the certificate;
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate[] createValidCertificateChain(String name) {
        X509Certificate[] result = new X509Certificate[1];
        result[0] = createCertificate(name, yesterday(), tomorrow());
        return result;
    }

    /**
     * Creates a new (expired) certificate valid from two days ago until yesterday.
     * 
     * @param name the (common) name of the certificate;
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate[] createExpiredCertificateChain(String name) {
        X509Certificate[] result = new X509Certificate[1];
        result[0] = createCertificate(name, dayBeforeYesterday(), yesterday());
        return result;
    }

    /**
     * Creates a new (not yet valid) certificate valid from tomorrow until the day after tomorrow.
     * 
     * @param name the (common) name of the certificate;
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate[] createNotValidCertificateChain(String name) {
        X509Certificate[] result = new X509Certificate[1];
        result[0] = createCertificate(name, tomorrow(), dayAfterTomorrow());
        return result;
    }
}
