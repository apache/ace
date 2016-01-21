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

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Provides a memory-only certificate keystore.
 */
final class MemoryKeyStore {
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final KeyPair m_caKey;
    private final X509Certificate m_rootCert;
    private int m_serial = 0;

    private final KeyPairGenerator m_generator;

    /**
     * Creates a new {@link MemoryKeyStore} instance.
     */
    public MemoryKeyStore(String name, Date notBefore, Date notAfter) {
        try {
            m_generator = KeyPairGenerator.getInstance("RSA");
            m_generator.initialize(1024);

            m_caKey = generateKeyPair();
            m_rootCert = generateRootCertificate(name, notBefore, notAfter);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @throws IllegalStateException
     *             if an internal exception occurs.
     * @throws IllegalArgumentException
     *             if the alias already exists.
     */
    public X509Certificate createCertificate(String name, Date before, Date after, PublicKey key) throws IllegalArgumentException {
        return createCertificate(getCA_DN(), m_caKey.getPrivate(), name, before, after, key);
    }

    /**
     * @throws IllegalStateException
     *             if an internal exception occurs.
     * @throws IllegalArgumentException
     *             if the alias already exists.
     */
    public X509Certificate createCertificate(X500Principal issuerDN, PrivateKey issuerKey, String name, Date notBefore, Date notAfter, PublicKey key) throws IllegalArgumentException {
        try {
            X500Name issuer = new X500Name(issuerDN.getName());
            X500Name commonName = new X500Name(name);
            BigInteger serial = BigInteger.valueOf(++m_serial);

            SubjectPublicKeyInfo pubKeyInfo = convertToSubjectPublicKeyInfo(key);

            X509v3CertificateBuilder builder = new X509v3CertificateBuilder(issuer, serial, notBefore, notAfter, commonName, pubKeyInfo);

            X509CertificateHolder certHolder = builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKey));
            return new JcaX509CertificateConverter().getCertificate(certHolder);
        }
        catch (IllegalArgumentException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a new 1024-bit keypair.
     * 
     * @return a new {@link KeyPair}, never <code>null</code>.
     */
    public KeyPair generateKeyPair() {
        try {
            return m_generator.generateKeyPair();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return
     */
    public X500Principal getCA_DN() {
        return m_rootCert.getIssuerX500Principal();
    }

    /**
     * @return the {@link KeyPair} of the CA, never <code>null</code>.
     */
    public KeyPair getCA_KeyPair() {
        return m_caKey;
    }

    private SubjectPublicKeyInfo convertToSubjectPublicKeyInfo(PublicKey key) throws IOException {
        try (ASN1InputStream is = new ASN1InputStream(key.getEncoded())) {
            return SubjectPublicKeyInfo.getInstance(is.readObject());
        }
    }

    private X509Certificate generateRootCertificate(String commonName, Date notBefore, Date notAfter) throws Exception {
        X500Name issuer = new X500Name(commonName);
        BigInteger serial = BigInteger.probablePrime(16, new Random());

        SubjectPublicKeyInfo pubKeyInfo = convertToSubjectPublicKeyInfo(m_caKey.getPublic());

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(issuer, serial, notBefore, notAfter, issuer, pubKeyInfo);
        builder.addExtension(new Extension(Extension.basicConstraints, true, new DEROctetString(new BasicConstraints(true))));

        X509CertificateHolder certHolder = builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(m_caKey.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }
}
