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
package org.apache.ace.client.repository.helper.bundle.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.osgi.framework.Constants;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class BundleHelperTest {
    private BundleHelperImpl m_helper;

    @BeforeTest
    public void setUp() throws Exception {
        m_helper = new BundleHelperImpl();
    }

    @Test(groups = { UNIT })
    public void testMimetype() {
        assert m_helper.canHandle("application/vnd.osgi.bundle") : "Should be able to handle bundle mimetype.";
        assert !m_helper.canHandle("somecrazy/mimetype") : "Should not be able to handle crazy mimetype.";
    }

    @Test(groups = { UNIT })
    public void testManifestExtraction() {
        ArtifactResource artifact = new ArtifactResource() {

            @Override
            public InputStream openStream() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Manifest manifest = new Manifest();
                Attributes attrs = manifest.getMainAttributes();
                attrs.putValue("Manifest-Version", "1");
                attrs.putValue("Bundle-SymbolicName", "mybundle");
                attrs.putValue("Bundle-Version", "1.0.0");
                attrs.putValue("Bundle-Name", "My Cool Bundle");
                JarOutputStream jos = new JarOutputStream(baos, manifest);
                jos.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public URL getURL() {
                return null;
            }
        };
        Map<String, String> map = m_helper.extractMetaData(artifact);
        assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
        assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
        assert "My Cool Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'My Cool Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }

    @Test(groups = { UNIT })
    public void testLocalizedManifestExtraction() {
        ArtifactResource artifact = new ArtifactResource() {

            @Override
            public InputStream openStream() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Manifest manifest = new Manifest();
                Attributes attrs = manifest.getMainAttributes();
                attrs.putValue("Manifest-Version", "1");
                attrs.putValue("Bundle-SymbolicName", "mybundle");
                attrs.putValue("Bundle-Version", "1.0.0");
                attrs.putValue("Bundle-Name", "%bundleName");
                attrs.putValue("Bundle-Localization", "locale");
                JarOutputStream jos = new JarOutputStream(baos, manifest);
                jos.putNextEntry(new ZipEntry("locale.properties"));
                String content = "bundleName=The Coolest Bundle";
                jos.write(content.getBytes(), 0, content.getBytes().length);
                jos.closeEntry();
                jos.close();

                // if you want to validate that the bundle is okay
                // FileOutputStream fos = new FileOutputStream(new File("/Users/marceloffermans/unittest.jar"));
                // fos.write(baos.toByteArray(), 0, baos.size());
                // fos.close();

                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public URL getURL() {
                return null;
            }
        };
        Map<String, String> map = m_helper.extractMetaData(artifact);
        assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
        assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
        assert "The Coolest Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'The Coolest Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }

    @Test(groups = { UNIT })
    public void testLocalizedManifestExtractionWithDefaultBase() {

        // note that we do not set the Bundle-Localization header

        ArtifactResource artifact = new ArtifactResource() {

            @Override
            public InputStream openStream() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Manifest manifest = new Manifest();
                Attributes attrs = manifest.getMainAttributes();
                attrs.putValue("Manifest-Version", "1");
                attrs.putValue("Bundle-SymbolicName", "mybundle");
                attrs.putValue("Bundle-Version", "1.0.0");
                attrs.putValue("Bundle-Name", "%bundleName");
                JarOutputStream jos = new JarOutputStream(baos, manifest);
                jos.putNextEntry(new ZipEntry(Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME + ".properties"));
                String content = "bundleName=The Coolest Bundle";
                jos.write(content.getBytes(), 0, content.getBytes().length);
                jos.closeEntry();
                jos.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public URL getURL() {
                return null;
            }
        };
        Map<String, String> map = m_helper.extractMetaData(artifact);
        assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
        assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
        assert "The Coolest Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'The Coolest Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }

    @Test(groups = { UNIT })
    public void testLocalizedManifestExtractionWithLocale() {
        ArtifactResource artifact = new ArtifactResource() {

            @Override
            public InputStream openStream() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Manifest manifest = new Manifest();
                Attributes attrs = manifest.getMainAttributes();
                attrs.putValue("Manifest-Version", "1");
                attrs.putValue("Bundle-SymbolicName", "mybundle");
                attrs.putValue("Bundle-Version", "1.0.0");
                attrs.putValue("Bundle-Name", "%bundleName");
                attrs.putValue("Bundle-Localization", "locale");
                JarOutputStream jos = new JarOutputStream(baos, manifest);
                jos.putNextEntry(new ZipEntry("locale_nl.properties"));
                String content = "bundleName=De koelste Bundle";
                jos.write(content.getBytes(), 0, content.getBytes().length);
                jos.closeEntry();
                jos.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public URL getURL() {
                return null;
            }
        };
        Map<String, String> map = m_helper.extractMetaData(artifact);
        assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
        assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
        assert "De koelste Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'The Coolest Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }

    @Test(groups = { UNIT })
    public void testLocalizedManifestExtractionWithLocaleOverrule() {
        ArtifactResource artifact = new ArtifactResource() {

            @Override
            public InputStream openStream() throws IOException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Manifest manifest = new Manifest();
                Attributes attrs = manifest.getMainAttributes();
                attrs.putValue("Manifest-Version", "1");
                attrs.putValue("Bundle-SymbolicName", "mybundle");
                attrs.putValue("Bundle-Version", "1.0.0");
                attrs.putValue("Bundle-Name", "%bundleName");
                attrs.putValue("Bundle-Localization", "locale");
                JarOutputStream jos = new JarOutputStream(baos, manifest);
                jos.putNextEntry(new ZipEntry("locale.properties"));
                String content = "bundleName=De koelste Bundle";
                jos.write(content.getBytes(), 0, content.getBytes().length);
                jos.closeEntry();
                jos.putNextEntry(new ZipEntry("locale_" + Locale.ENGLISH + ".properties"));
                String contentEN = "bundleName=A damn fine Bundle";
                jos.write(contentEN.getBytes(), 0, contentEN.getBytes().length);
                jos.closeEntry();
                jos.putNextEntry(new ZipEntry("locale_" + Locale.US + ".properties"));
                String contentUS = "bundleName=The Coolest Bundle";
                jos.write(contentUS.getBytes(), 0, contentUS.getBytes().length);
                jos.closeEntry();

                jos.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public URL getURL() {
                return null;
            }
        };
        Map<String, String> map = m_helper.extractMetaData(artifact);
        assert "mybundle".equals(map.get("Bundle-SymbolicName")) : "Symbolic name should have been 'mybundle', was " + map.get("Bundle-SymbolicName");
        assert "1.0.0".equals(map.get("Bundle-Version")) : "Version should have been '1.0.0', was " + map.get("Bundle-Version");
        assert "The Coolest Bundle-1.0.0".equals(map.get(ArtifactObject.KEY_ARTIFACT_NAME)) : "Artifact name should have been 'The Coolest Bundle-1.0.0', was " + map.get(ArtifactObject.KEY_ARTIFACT_NAME);
    }
}
