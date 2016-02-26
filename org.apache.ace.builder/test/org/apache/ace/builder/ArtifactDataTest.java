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
package org.apache.ace.builder;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link ArtifactData}.
 */
public class ArtifactDataTest {
    private URL m_fakeURL;
    private String m_filename;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws IOException {
        File tmpFile = File.createTempFile("artifactData", ".tmp");
        tmpFile.deleteOnExit();

        m_filename = tmpFile.getAbsolutePath();
        m_fakeURL = tmpFile.toURI().toURL();
    }

    @Test()
    public void testCreateArtifactWithoutParametersOk() throws Exception {
        ArtifactData artifact = ArtifactData.createArtifact(m_fakeURL, m_filename, "rp");

        assertNotNull(artifact);
        assertNull(artifact.getSymbolicName());
        assertNull(artifact.getVersion());
        assertEquals(artifact.getProcessorPid(), "rp");
        assertFalse(artifact.isCustomizer());
        assertFalse(artifact.isBundle());
    }

    @Test()
    public void testCreateBundleWithoutParametersOk() throws Exception {
        ArtifactData artifact = ArtifactData.createBundle(m_fakeURL, m_filename, "my.bundle.name", "1.0.0");

        assertNotNull(artifact);
        assertEquals(artifact.getSymbolicName(), "my.bundle.name");
        assertEquals(artifact.getVersion(), "1.0.0");
        assertNull(artifact.getProcessorPid());
        assertFalse(artifact.isCustomizer());
        assertTrue(artifact.isBundle());
    }

    @Test()
    public void testCreateBundleWithParametersOk() throws Exception {
        ArtifactData artifact = ArtifactData.createBundle(m_fakeURL, m_filename, "my.bundle.name;singleton:=true, foo:=bar", "1.0.0");

        assertNotNull(artifact);
        assertEquals(artifact.getSymbolicName(), "my.bundle.name");
        assertEquals(artifact.getVersion(), "1.0.0");
        assertNull(artifact.getProcessorPid());
        assertFalse(artifact.isCustomizer());
        assertTrue(artifact.isBundle());
    }

    @Test()
    public void testCreateResourceProcessorWithoutParametersOk() throws Exception {
        ArtifactData artifact = ArtifactData.createResourceProcessor(m_fakeURL, m_filename, "my.bundle.name", "1.0.0", "rp");

        assertNotNull(artifact);
        assertEquals(artifact.getSymbolicName(), "my.bundle.name");
        assertEquals(artifact.getVersion(), "1.0.0");
        assertEquals(artifact.getProcessorPid(), "rp");
        assertTrue(artifact.isCustomizer());
        assertTrue(artifact.isBundle());
    }

    @Test()
    public void testCreateResourceProcessorWithParametersOk() throws Exception {
        ArtifactData artifact = ArtifactData.createResourceProcessor(m_fakeURL, m_filename, "my.bundle.name;singleton:=true, foo:=bar", "1.0.0", "rp;qux:=quu");

        assertNotNull(artifact);
        assertEquals(artifact.getSymbolicName(), "my.bundle.name");
        assertEquals(artifact.getVersion(), "1.0.0");
        assertEquals(artifact.getProcessorPid(), "rp");
        assertTrue(artifact.isCustomizer());
        assertTrue(artifact.isBundle());
    }
}
