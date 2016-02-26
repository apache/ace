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
package org.apache.ace.client.repository.helper.base;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.service.useradmin.User;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test cases for {@link VelocityArtifactPreprocessor}.
 */
public class VelocityArtifactPreprocessorTest {
    
    private static final String TARGET = "target";
    private static final String VERSION1 = "1.0.0";
    
    private URL m_obrUrl;
    private PropertyResolver m_resolver;
    
    @BeforeTest
    public void setUp() throws Exception {
        m_obrUrl = new URL("file://" + System.getProperty("java.io.tmpdir") + "/");
        
        m_resolver = new PropertyResolver() {
            public String get(String key) {
                return "msg".equals(key) ? "Hello World!" : null;
            }
        };
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#needsNewVersion(String, PropertyResolver, String, String)}
     */
    @Test()
    public void testNeedsNewVersionChangedTemplateOk() throws Exception {
        final VelocityArtifactPreprocessor vap = createProcessor();
        
        String url = createArtifact("Message: [$context.msg]");

        // "upload" a new version...
        vap.preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);
        
        boolean result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertFalse(result); // no new version is needed...
        
        updateArtifact(url, "Another message: [$context.msg2]");
        
        result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertFalse(result); // no new version is needed; original artifact is cached indefinitely...
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#needsNewVersion(String, PropertyResolver, String, String)}
     */
    @Test()
    public void testNeedsNewVersionEmptyTemplateOk() throws Exception {
        final VelocityArtifactPreprocessor vap = createProcessor();
        
        String url = createArtifact("");

        // "upload" a new version...
        vap.preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);

        boolean result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertFalse(result); // no new version is needed...
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#needsNewVersion(String, PropertyResolver, String, String)}
     */
    @Test()
    public void testNeedsNewVersionNonExistingTemplateOk() throws Exception {
        final VelocityArtifactPreprocessor vap = createProcessor();
        
        // Should be something that really doesn't exist somehow...
        String url = "file:///path/to/nowhere-" + System.currentTimeMillis();

        boolean result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertTrue(result); // always true for non-existing templates...
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#needsNewVersion(String, PropertyResolver, String, String)}
     */
    @Test()
    public void testNeedsNewVersionUnchangedTemplateOk() throws Exception {
        final VelocityArtifactPreprocessor vap = createProcessor();
        
        String url = createArtifact("Message: [$context.msg]");
        
        boolean result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertTrue(result); // nothing uploaded yet; new version is needed...

        // "upload" a new version...
        vap.preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);

        result = vap.needsNewVersion(url, m_resolver, TARGET, VERSION1);
        assertFalse(result); // no new version is needed...
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#preprocess(String, PropertyResolver, String, String, java.net.URL)}
     */
    @Test()
    public void testPreprocessExistingNoTemplateOk() throws Exception {
        String url = createArtifact("Message: [context.msg]");
        
        String newUrl = createProcessor().preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);
        assertNotNull(newUrl);
        // Verify that it is *not* uploaded...
        assertEquals(url, newUrl);
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#preprocess(String, PropertyResolver, String, String, java.net.URL)}
     */
    @Test()
    public void testPreprocessExistingRealTemplateOk() throws Exception {
        String url = createArtifact("Message: [$context.msg]");
        
        String newUrl = createProcessor().preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);
        assertNotNull(newUrl);
        // Verify that it is actually uploaded...
        assertFalse(newUrl.equals(url));
        // Verify that it is actually uploaded to our (fake) OBR...
        assertTrue(newUrl.startsWith(m_obrUrl.toExternalForm()), "newUrl (" + newUrl + ") should start with: " + m_obrUrl.toExternalForm());
    }

    /**
     * Test case for {@link VelocityArtifactPreprocessor#preprocess(String, PropertyResolver, String, String, java.net.URL)}
     */
    @Test(expectedExceptions = { IOException.class })
    public void testPreprocessNonExistingTemplateOk() throws Exception {
        // Should be something that really doesn't exist somehow...
        String url = "file:///path/to/nowhere-" + System.currentTimeMillis();
        
        createProcessor().preprocess(url, m_resolver, TARGET, VERSION1, m_obrUrl);
    }

    private String createArtifact(String string) throws IOException {
        File tmpFile = File.createTempFile("vap", "vm");
        tmpFile.delete();
        tmpFile.deleteOnExit();
        
        FileWriter writer = new FileWriter(tmpFile);
        writer.write(string);
        writer.flush();
        writer.close();

        return tmpFile.toURI().toURL().toExternalForm();
    }

    private VelocityArtifactPreprocessor createProcessor() {
        return new VelocityArtifactPreprocessor(new ConnectionFactory() {
            public URLConnection createConnection(URL url, User user) throws IOException {
                return createConnection(url);
            }

            public URLConnection createConnection(URL url) throws IOException {
                return url.openConnection();
            }
        });
    }

    private String updateArtifact(String url, String string) throws IOException {
        URL uri = new URL(url);
        
        FileWriter writer = new FileWriter(uri.getFile());
        writer.write(string);
        writer.flush();
        writer.close();

        return url;
    }
}
