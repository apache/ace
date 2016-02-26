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

import static org.apache.ace.authentication.processor.clientcert.ClientCertAuthenticationProcessor.ATTRIBUTE_CIPHER_SUITE;
import static org.apache.ace.authentication.processor.clientcert.ClientCertAuthenticationProcessor.ATTRIBUTE_X509_CERTIFICATE;
import static org.apache.ace.authentication.processor.clientcert.ClientCertAuthenticationProcessor.PROPERTY_USERNAME_LOOKUPKEY;
import static org.apache.ace.authentication.processor.clientcert.ClientCertAuthenticationProcessor.PROPERTY_USERNAME_MATCH_POLICY;
import static org.apache.ace.authentication.processor.clientcert.ClientCertAuthenticationProcessor.PROPERTY_VERIFY_CERT_VALIDITY;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.security.auth.x500.X500Principal;
import javax.servlet.http.HttpServletRequest;

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

        when(m_servletRequest.getAttribute(ATTRIBUTE_CIPHER_SUITE)).thenReturn("bogus-cipher-suite");
    }

    /**
     * Tests that a null certificate chain will yield null.
     */
    @Test()
    public void testAuthenticateNoCertificateChainYieldsNull() {
        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that an empty certificate chain will yield null.
     */
    @Test()
    public void testAuthenticateEmptyCertificateChainYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(new X509Certificate[0]);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that authenticating a known user with an invalid (expired) certificate will yield null.
     */
    @Test()
    public void testAuthenticateKnownUserWithExpiredCertificateYieldsNull() {
        X509Certificate[] certificateChain = createExpiredCertificateChain("bob");
        PublicKey publickey = certificateChain[0].getPublicKey();

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certificateChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");
        when(user.hasCredential(eq("publickey"), eq(publickey.getEncoded()))).thenReturn(Boolean.TRUE);

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that authenticating a known user with an invalid (not valid) certificate will yield null.
     */
    @Test()
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
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that authenticating a known user with a valid certificate will not yield null.
     */
    @Test()
    public void testAuthenticateKnownUserYieldsValidResult() {
        X509Certificate[] certChain = createValidCertificateChain("bob");

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");

        when(m_userAdmin.getUser(eq("username"), eq("bob"))).thenReturn(user);

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNotNull(result, "Expected a valid user to be returned!");

        assertEquals(user.getName(), "bob", "Expected bob to be returned as user!");
    }

    /**
     * Tests that authenticating a known user with a valid certificate chain will not yield null.
     */
    @Test()
    public void testAuthenticateKnownUserWithValidCertificateChainYieldsValidResult() throws ConfigurationException {
        ClientCertAuthenticationProcessor processor = createAuthorizationProcessor();

        final String lookupKey = "anyKey";
        final String matchPolicy = "dn";

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, lookupKey);
        props.put(PROPERTY_USERNAME_MATCH_POLICY, matchPolicy);
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");
        processor.updated(props);

        X509Certificate[] certChain = createValidCertificateChainWithDN("cn=Alice,dc=acme,dc=corp", "cn=Fido,ou=dev,dc=acme,dc=corp", "cn=Bob,ou=dev,dc=acme,dc=corp");

        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("bob");

        when(m_userAdmin.getUser(eq(lookupKey), eq("DC=corp,DC=acme,OU=dev,CN=Bob"))).thenReturn(user);

        User result = processor.authenticate(m_userAdmin, m_servletRequest);
        assertNotNull(result, "Expected a valid user to be returned!");

        assertEquals(user.getName(), "bob", "Expected bob to be returned as user!");
    }

    /**
     * Tests that a missing cipher suite header will the authenticate method to yield null.
     */
    @Test()
    public void testAuthenticateMissingCipherSuiteHeaderYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_CIPHER_SUITE)).thenReturn(null);
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(createValidCertificateChain("bob"));

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that a class cast exception is thrown for invalid context when calling authenticate.
     */
    @Test(expectedExceptions = ClassCastException.class)
    public void testAuthenticateThrowsClassCastForInvalidContext() {
        createAuthorizationProcessor().authenticate(m_userAdmin, new Object());
    }

    /**
     * Tests that an unknown user will yield null.
     */
    @Test()
    public void testAuthenticateUnknownUserYieldsNull() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(createValidCertificateChain("bob"));

        User result = createAuthorizationProcessor().authenticate(m_userAdmin, m_servletRequest);
        assertNull(result, "Did not expect a valid user to be returned!");
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test()
    public void testCanHandleDoesAcceptServletRequest() {
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(createValidCertificateChain("alice"));

        assertTrue(createAuthorizationProcessor().canHandle(m_servletRequest));
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for an empty context.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptEmptyArray() {
        createAuthorizationProcessor().canHandle(new Object[0]);
    }

    /**
     * Tests that canHandle throws an {@link IllegalArgumentException} for a null context.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCanHandleDoesNotAcceptNull() {
        createAuthorizationProcessor().canHandle((Object[]) null);
    }

    /**
     * Tests that canHandle yields false for any object other than {@link HttpServletRequest}.
     */
    @Test()
    public void testCanHandleDoesNotAcceptUnhandledContext() {
        assertFalse(createAuthorizationProcessor().canHandle(new Object()));
    }

    /**
     * Tests that updated does not throw an exception for a correct configuration.
     */
    @Test()
    public void testUpdatedDoesAcceptCorrectProperties() throws ConfigurationException {
        final String lookupKey = "anyKey";
        final String matchPolicy = "cn";

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, lookupKey);
        props.put(PROPERTY_USERNAME_MATCH_POLICY, matchPolicy);
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");

        ClientCertAuthenticationProcessor processor = createAuthorizationProcessor();

        processor.updated(props);

        X509Certificate[] certificateChain = createValidCertificateChain("alice");

        // Test whether we can use the new properties...
        when(m_servletRequest.getAttribute(ATTRIBUTE_X509_CERTIFICATE)).thenReturn(certificateChain);

        User user = mock(User.class);
        when(user.getName()).thenReturn("alice");

        when(m_userAdmin.getUser(eq(lookupKey), eq("alice"))).thenReturn(user);

        User result = processor.authenticate(m_userAdmin, m_servletRequest);
        assertNotNull(result, "Expected a valid user to be returned!");

        assertEquals(user.getName(), "alice", "Expected alice to be returned as user!");
    }

    /**
     * Tests that updated throws an exception for missing "username match policy" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyMatchPolicy() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, "foo");
        props.put(PROPERTY_USERNAME_MATCH_POLICY, "");
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "username lookup key" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyLookupKey() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, "");
        props.put(PROPERTY_USERNAME_MATCH_POLICY, "foo");
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "verify cert validity" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptEmptyVerifyCertValidity() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, "foo");
        props.put(PROPERTY_USERNAME_MATCH_POLICY, "bar");
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "username match policy" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingMatchPolicy() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, "foo");
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "user name lookup key" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingUsernameLookupKey() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_MATCH_POLICY, "foo");
        props.put(PROPERTY_VERIFY_CERT_VALIDITY, "true");

        createAuthorizationProcessor().updated(props);
    }

    /**
     * Tests that updated throws an exception for missing "verify cert validity" property.
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void testUpdatedDoesNotAcceptMissingVerifyCertValidity() throws ConfigurationException {
        Dictionary<String, Object> props = new Hashtable<>();

        props.put(PROPERTY_USERNAME_LOOKUPKEY, "foo");
        props.put(PROPERTY_USERNAME_MATCH_POLICY, "foo");

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
     * @param name
     *            the (common) name of the certificate;
     * @param notBefore
     *            the date after which the certificate is valid;
     * @param notAfter
     *            the date until the certificate is valid.
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate createCertificate(String name, final Date notBefore, final Date notAfter) {
        KeyPair keypair = m_keystore.generateKeyPair();
        return m_keystore.createCertificate("cn=" + name, notBefore, notAfter, keypair.getPublic());
    }

    /**
     * Creates a new (valid) chain with certificate(s) valid from yesterday until tomorrow.
     * 
     * @param dns
     *            the distinguished names of the certificates in the returned chain.
     * @return a new chain with {@link X509Certificate}s, never <code>null</code>.
     */
    private X509Certificate[] createValidCertificateChainWithDN(String... dns) {
        X509Certificate[] result = new X509Certificate[dns.length];

        X500Principal signerDN = m_keystore.getCA_DN();
        KeyPair signerKeyPair = m_keystore.getCA_KeyPair();

        for (int i = 0; i < result.length; i++) {
            KeyPair certKeyPair = m_keystore.generateKeyPair();

            String dn = dns[i];
            int idx = result.length - i - 1;

            result[idx] = m_keystore.createCertificate(signerDN, signerKeyPair.getPrivate(), dn, yesterday(), tomorrow(), certKeyPair.getPublic());

            signerDN = result[idx].getSubjectX500Principal();
            signerKeyPair = certKeyPair;
        }
        return result;
    }

    /**
     * Creates a new (valid) certificate valid from yesterday until tomorrow.
     * 
     * @param name
     *            the (common) name of the certificate;
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
     * @param name
     *            the (common) name of the certificate;
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
     * @param name
     *            the (common) name of the certificate;
     * @return a new {@link X509Certificate}, never <code>null</code>.
     */
    private X509Certificate[] createNotValidCertificateChain(String name) {
        X509Certificate[] result = new X509Certificate[1];
        result[0] = createCertificate(name, tomorrow(), dayAfterTomorrow());
        return result;
    }
}
