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
package org.apache.ace.it.deployment.provider.filebased;

import static org.testng.Assert.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.ArtifactDataHelper;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.deployment.util.test.BundleStreamGenerator;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * This test class tests the FileBasedProvider class. This class implements 2 backend interfaces, and both are tested
 * here.
 */
public class FileBasedProviderTest {

    private FileBasedProvider m_backend;

    private File m_tempDirectory;

    private final String VERSION1 = "1.0.0";
    private final String VERSION2 = "2.0.0";
    private final String VERSION3 = "3.0.0";
    private final String VERSION4 = "4.0.0";
    private final String INVALIDVERSION = "Invalid.version.directory";

    private final String TARGET = "target";
    private final String MULTIPLEVERSIONTARGET = "multi-version-target";
    private final String INVALIDVERSIONTARGET = "illegal-version-target";
    private ArtifactData BUNDLE1;
    private ArtifactData BUNDLE3;
    private ArtifactData BUNDLE4;
    private ArtifactData BUNDLE4_1;
    private ArtifactData BUNDLE5;
    private ArtifactData BUNDLE3_2;
    private ArtifactData BUNDLE4_2;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {

        // first create a file
        m_tempDirectory = FileUtils.createTempFile(null);
        // and make a directory with that name.
        m_tempDirectory.mkdir();
        setupSampleData();

        m_backend = new FileBasedProvider();
        TestUtils.configureObject(m_backend, LogService.class);
        TestUtils.configureObject(m_backend, ArtifactDataHelper.class, new ArtifactDataHelper() {
            @Override
            public List<ArtifactData> process(List<ArtifactData> artifacts, String targetId, String versionFrom, String versionTo) {
                return artifacts;
            }
        });

        m_backend.updated(new Hashtable<String, String>() {
            {
                put("BaseDirectoryName", m_tempDirectory.getAbsolutePath());
            }
        });
    }

    /**
     * make a bundle with the given symbolic name and version in the given file.
     */
    private ArtifactData generateBundle(File file, String symbolicName, String version) throws Exception {
        ArtifactData bundle = new ArtifactDataImpl(file.getName(), symbolicName, file.length(), version, file.toURI().toURL(), false);
        BundleStreamGenerator.generateBundle(bundle);
        return bundle;
    }

    /**
     * Create the test targets, versions and testbundles..
     */
    private void setupSampleData() throws Exception {
        File target = new File(m_tempDirectory, TARGET);
        target.mkdirs();
        File targetVersion1 = new File(target, VERSION1);
        targetVersion1.mkdirs();
        BUNDLE1 = generateBundle(FileUtils.createTempFile(targetVersion1), "Bundle1", "1.0.0");

        File illegalVersionTarget = new File(m_tempDirectory, INVALIDVERSIONTARGET);
        illegalVersionTarget.mkdirs();
        File faultyVersion = new File(illegalVersionTarget, INVALIDVERSION);
        faultyVersion.mkdirs();
        // this bundle should never be accessed
        generateBundle(FileUtils.createTempFile(faultyVersion), "Bundle2", "2.0.0");

        File multipleVersionTarget = new File(m_tempDirectory, MULTIPLEVERSIONTARGET);
        multipleVersionTarget.mkdir();
        File multipleVersionTargetVersion1 = new File(multipleVersionTarget, VERSION1);
        multipleVersionTargetVersion1.mkdir();
        BUNDLE3 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion1), "Bundle3", "3.0.0");
        BUNDLE4 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion1), "Bundle4", "4.0.0");
        File multipleVersionTargetVersion2 = new File(multipleVersionTarget, VERSION2);
        multipleVersionTargetVersion2.mkdir();
        BUNDLE4_1 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion2), "Bundle4", "4.1.0");
        BUNDLE5 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion2), "Bundle5", "5.0.0");
        File multipleVersionTargetVersion3 = new File(multipleVersionTarget, VERSION3);
        multipleVersionTargetVersion3.mkdir();
        File multipleVersionTargetVersion4 = new File(multipleVersionTarget, VERSION4);
        multipleVersionTargetVersion4.mkdir();
        BUNDLE3_2 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion4), "Bundle3", "3.0.0");
        BUNDLE4_2 = generateBundle(FileUtils.createTempFile(multipleVersionTargetVersion4), "Bundle4", "5.0.0");
    }

    /**
     * See if the getVersions() methods normal output works
     */
    @Test()
    public void testGetVersion() {
        List<String> versions = m_backend.getVersions(TARGET);
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION1) : "Expected version " + VERSION1 + " but found " + versions.get(0);
    }

    /**
     * Test the getVersions method with an illegal version (not in org.osgi.framework.Version format)
     */
    @Test()
    public void testIllegalVersion() {
        // an illegal version should be silently ignored
        List<String> versions = m_backend.getVersions(INVALIDVERSIONTARGET);
        assert versions.isEmpty() : "Expected no versions to be found, but found " + versions.size();
    }

    /**
     * Test with multiple versions. It expects all versions in an ascending order.
     */
    @Test()
    public void testMultipleVersions() {
        List<String> versions = m_backend.getVersions(MULTIPLEVERSIONTARGET);
        assert versions.size() == 4 : "Expected three version to be found, but found " + versions.size();
        // all versions should be in ascending order
        assert versions.get(0).equals(VERSION1) : "Expected version " + VERSION1 + " but found " + versions.get(0);
        assert versions.get(1).equals(VERSION2) : "Expected version " + VERSION2 + " but found " + versions.get(1);
        assert versions.get(2).equals(VERSION3) : "Expected version " + VERSION3 + " but found " + versions.get(2);
        assert versions.get(3).equals(VERSION4) : "Expected version " + VERSION4 + " but found " + versions.get(3);
    }

    /**
     * Tests that a {@link ArtifactDataHelper} instance can be used to mangle the returned artifact data.
     */
    @Test()
    public void testArtifactDataHelperIsUsed() {
        TestUtils.configureObject(m_backend, ArtifactDataHelper.class, new ArtifactDataHelper() {
            @Override
            public List<ArtifactData> process(List<ArtifactData> artifacts, String targetId, String versionFrom, String versionTo) {
                Collections.sort(artifacts, (a, b) -> b.getSymbolicName().compareTo(a.getSymbolicName()));
                return artifacts;
            }
        });

        // XXX
        List<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1);
        assertEquals(2, bundleData.size(), "Expected two bundle to be found, but found " + bundleData.size());
        assertEquals(BUNDLE4, bundleData.get(0), "Expected to find bundle " + BUNDLE4.getSymbolicName());
        assertEquals(BUNDLE3, bundleData.get(1), "Expected to find bundle " + BUNDLE3.getSymbolicName());
    }

    /**
     * Test the getBundleData for a single version, returning a single bundle
     */
    @Test()
    public void testSingleBundleSingleVersionBundleData() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, VERSION1);
        assert bundleData.size() == 1 : "Expected one bundle to be found, but found " + bundleData.size();
        assert bundleData.contains(BUNDLE1) : "Expected to find bundle " + BUNDLE1.getSymbolicName();
    }

    /**
     * Test the getBundleData for a single version, returning a multiple bundles
     */
    @Test()
    public void testMultipleBundleSingleVersionBundleData() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        assert bundleData.contains(BUNDLE3) : "Expected to find bundle " + BUNDLE3.getSymbolicName();
        assert bundleData.contains(BUNDLE4) : "Expected to find bundle " + BUNDLE4.getSymbolicName();
    }

    /**
     * Test the getBundleData with an illegal version (i.e. a version that doesn't exist)
     */
    @Test()
    public void testInvalidVersionBundleData() {
        try {
            m_backend.getBundleData(INVALIDVERSIONTARGET, INVALIDVERSION);
            assert false : "Expected an error because version " + INVALIDVERSION + " doesn't exist for target" + INVALIDVERSIONTARGET;
        }
        catch (IllegalArgumentException iae) {
            // expected, because the version doesn't exist
        }
    }

    /**
     * Test the getBundleData for a two versions, returning a single bundle that hasn't changed
     */
    @Test()
    public void testSingleUnchangedBundleMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, VERSION1, VERSION1);
        assert bundleData.size() == 1 : "Expect one bundle, got " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            assert data.getSize() > 200 : "Bundle has no sensible size?!";
            assert !data.hasChanged() : "The data should not have been changed.";
        }
    }

    /**
     * Test the getBundleData for a two versions, returning multiple bundles that haven't changed
     */
    @Test()
    public void testMultipleBundlesMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            assert !data.hasChanged() : "The data should not have been changed.";
        }
    }

    /**
     * Test the getBundleData for a two versions, where in the second version a bundle is removed
     */
    @Test()
    public void testRemovedBundleMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION3);
        assert bundleData.size() == 0 : "Expected zero bundle to be found, but found " + bundleData.size();
    }

    /**
     * Test the getBundleData for a two versions, where in the second version a bundle is added
     */
    @Test()
    public void testAddedBundleMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION3, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            assert data.hasChanged() : "The data should have been changed.";
        }
    }

    /**
     * Test the getBundleData for a two versions, where in the second version one bundle has changed and another hasn't
     */
    @Test()
    public void testSingleChangedBundleMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION4);
        assert bundleData.size() == 2 : "Expected one bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.equals(BUNDLE3_2)) {
                assert !data.hasChanged() : "The data should not have been changed.";
            }
            else if (data.equals(BUNDLE4_2)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else {
                assert false : "Unknown bundle found";
            }
        }
    }

    /**
     * Test the getBundleData for a two versions, where two bundles have changed
     */
    @Test()
    public void testMultipleChangedBundlesMultipleVersions() {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION2);
        assert bundleData.size() == 2 : "Expected one bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.equals(BUNDLE4_1)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else if (data.equals(BUNDLE5)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else {
                assert false : "Unknown bundle found";
            }
        }
    }

    @AfterTest(alwaysRun = true)
    public void tearDown() throws Exception {
        FileUtils.removeDirectoryWithContent(m_tempDirectory);
    }

}
