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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509V1CertificateGenerator;

/**
 * Provides a memory-only certificate keystore.
 */
final class MemoryKeyStore {
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA"; // MD5withRSA

    private final X509V1CertificateGenerator m_certGen = new X509V1CertificateGenerator();
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
     * Generates a new 512-bit keypair.
     * 
     * @return a new {@link KeyPair}, never <code>null</code>.
     */
    public final KeyPair generateKeyPair() {
        try {
            return m_generator.generateKeyPair();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @throws IllegalStateException if an internal exception occurs.
     * @throws IllegalArgumentException if the alias already exists.
     */
    public X509Certificate createCertificate(String alias, String name, Date before, Date after, PublicKey key)
        throws IllegalStateException, IllegalArgumentException {
        try {
            m_certGen.reset();
            m_certGen.setSerialNumber(BigInteger.valueOf(++m_serial));
            m_certGen.setIssuerDN(m_rootCert.getIssuerX500Principal());
            m_certGen.setNotBefore(before);
            m_certGen.setNotAfter(after);
            m_certGen.setSubjectDN(new X500Principal(name));
            m_certGen.setPublicKey(key);
            m_certGen.setSignatureAlgorithm(SIGNATURE_ALGORITHM);

            X509Certificate cert = m_certGen.generate(m_caKey.getPrivate());

            return cert;
        }
        catch (IllegalArgumentException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate generateRootCertificate(String name, Date notBefore, Date notAfter) throws Exception {
        m_certGen.reset();
        m_certGen.setSerialNumber(BigInteger.valueOf(1));
        m_certGen.setIssuerDN(new X500Principal(name));
        m_certGen.setNotBefore(notBefore);
        m_certGen.setNotAfter(notAfter);
        m_certGen.setSubjectDN(new X500Principal(name));
        m_certGen.setPublicKey(m_caKey.getPublic());
        m_certGen.setSignatureAlgorithm(SIGNATURE_ALGORITHM);

        return m_certGen.generate(m_caKey.getPrivate());
    }
}
