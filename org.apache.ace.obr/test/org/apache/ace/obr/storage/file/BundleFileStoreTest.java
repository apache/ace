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
package org.apache.ace.obr.storage.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.storage.OBRFileStoreConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BundleFileStoreTest {

    private BundleFileStore m_bundleStore;
    private MockMetadataGenerator m_metadata;

    private File m_directory;

    private File m_bundleSubstitute1;
    private File m_bundleSubstitute1Larger;
    private File m_bundleSubstitute2;
    private File m_bundleSubstitute3;
    private File m_bundleRepositoryFile;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_bundleStore = new BundleFileStore();

        m_directory = FileUtils.createTempFile(null);
        m_directory.mkdir();

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, m_directory.getAbsolutePath());
        m_bundleStore.updated(props);

        // set a null object on for log
        TestUtils.configureObject(m_bundleStore, LogService.class);

        // create a mock MetadataGenerator
        m_metadata = new MockMetadataGenerator();
        TestUtils.configureObject(m_bundleStore, MetadataGenerator.class, m_metadata);

        // create some bundles to work with
        m_bundleSubstitute1 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub1.jar", 1000);
        m_bundleSubstitute2 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub2.jar", 2000);
        m_bundleSubstitute3 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub3.jar", 3000);
        m_bundleRepositoryFile = createFileWithContent(m_directory.getAbsoluteFile(), "index.xml", 1000);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        m_bundleSubstitute1.delete();
        m_bundleSubstitute2.delete();
        m_bundleSubstitute3.delete();
        m_bundleRepositoryFile.delete();
        m_directory.delete();
    }

    /**
     * Test whether the metadata is generated when getting a bundle from the repository.
     */
    @Test()
    public void getBundle() throws Exception {
        m_bundleStore.get(m_bundleSubstitute1.getName());
        assert !m_metadata.generated() : "During getting a bundle, the metadata should not be regenerated.";
    }

    /**
     * Test that the bundle store reutrns null for non-existing files.
     */
    @Test()
    public void getNonExistingBundle() throws Exception {
        assert m_bundleStore.get("blaat") == null : "Getting an non-existing file did not result in null?";
    }

    /**
     * Test whether retrieving the index.xml results in a call to the (mock) metadata generator, and the original file
     * should correspond with the retrieved file.
     */
    @Test()
    public void getRepositoryFile() throws Exception {
        InputStream newInputStream = m_bundleStore.get("index.xml");
        assert m_metadata.generated() : "During getting the repository file, the metadata should be regenerated.";

        byte[] orgContentBuffer = new byte[1000];
        newInputStream.read(orgContentBuffer);

        FileInputStream orgInputStream = new FileInputStream(m_bundleRepositoryFile);
        byte[] newContentBuffer = new byte[1000];
        orgInputStream.read(newContentBuffer);
        orgInputStream.close();

        assert Arrays.equals(orgContentBuffer, newContentBuffer) : "The original index.xml content should equal the newly retrieved content.";
    }

    /**
     * Test whether the BundleStore notices the set of bundles has changed (bundle updated), and makes a call to the
     * (mock) metadata generator.
     */
    @Test()
    public void updateBundle() throws Exception {
        m_bundleStore.get("index.xml");
        assert m_metadata.numberOfCalls() == 1 : "The MetadataGenerator should be called once";

        m_bundleSubstitute1Larger = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub1.jar", 2000);

        m_bundleStore.get("index.xml");
        assert m_metadata.numberOfCalls() == 2 : "The MetadataGenerator should be called twice";

        // test specific tear down
        m_bundleSubstitute1Larger.delete();
    }

    /**
     * Test whether the BundleStore notices the set of bundles has changed (bundle added), and makes a call to the
     * (mock) metadata generator. Also a call should be made when a bundle is replaced by another one (number of bundles
     * stay the same, but one bundle is replaced by another).
     */
    @Test()
    public void addBundle() throws Exception {
        m_bundleStore.get("index.xml");
        assert m_metadata.numberOfCalls() == 1 : "The MetadataGenerator should be called once";

        File bundleSubstituteX = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSubX.jar", 2000);

        m_bundleStore.get("index.xml");
        assert m_metadata.numberOfCalls() == 2 : "The MetadataGenerator should be called twice";

        bundleSubstituteX.delete();

        File bundleSubstituteY = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSubY.jar", 2000);

        m_bundleStore.get("index.xml");
        assert m_metadata.numberOfCalls() == 3 : "The MetadataGenerator should be called three times";

        // test specific tear down
        bundleSubstituteY.delete();
    }

    /**
     * Test whether the BundleStore notices the set of bundles has not changed, and thus will not make a call to the
     * (mock) metadata generator.
     */
    @Test()
    public void replaceWithSameBundle() throws Exception {
        m_bundleStore.get("bundleSub1.jar");
        assert m_metadata.numberOfCalls() == 0 : "The MetadataGenerator should not be called";

        FileInputStream inputStream = new FileInputStream(m_bundleSubstitute1);
        byte[] buffer = new byte[1000];
        inputStream.read(buffer);
        inputStream.close();
        m_bundleSubstitute1.delete();

        File newFile = new File(m_directory, "bundleSub1.jar");
        FileOutputStream outputStream = new FileOutputStream(newFile);

        outputStream.write(buffer);
        outputStream.close();

        m_bundleStore.get("bundleSub1.jar");
        assert m_metadata.numberOfCalls() == 0 : "The MetadataGenerator should still not be called";
    }

    /**
     * Test whether changing the directory where the bundles are stored, does not result in a call to the (mock)
     * metadata generator, as the metadata will only be regenerated after getting a file.
     */
    @Test()
    public void updateConfigurationWithValidConfiguration() throws Exception {
        File subDir = new File(m_directory.getAbsolutePath(), "changedDirectory");
        subDir.mkdir();

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, subDir.getAbsolutePath());
        try {
            m_bundleStore.updated(props);
        }
        catch (ConfigurationException e) {
            assert false : "Reconfiguring directory failed, directory was '" + m_directory + "' but should be '" + subDir + "'";
        }

        assert !m_metadata.generated() : "After changing the directory, the metadata should not be regenerated.";

        // test specific tear down
        subDir.delete();
    }

    /**
     * Test whether changing the directory where the bundles are stored to something that is not a directory, this
     * should fail.
     */
    @Test()
    public void updateConfigurationWithIsNotDirectory() throws Exception {
        boolean exceptionThrown = false;

        File file = new File(m_directory.getAbsolutePath(), "file");
        file.createNewFile();

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, file.getAbsolutePath());
        try {
            m_bundleStore.updated(props);
        }
        catch (ConfigurationException e) {
            // exception should be thrown as attempting to configure with File that is no directory
            exceptionThrown = true;
        }
        assert exceptionThrown : "Reconfiguring directory succeeded, but should fail as it is no directory";

        // test specific tear down
        file.delete();
    }

    @Test()
    public void putBundle() throws Exception {
        File bundle = createTmpResource("foo.bar", "1.0.0");
        String filePath = m_bundleStore.put(new FileInputStream(bundle), null, false);
        assert filePath.equals("foo/foo.bar-1.0.0.jar") : "Path should be 'foo/foo.bar-1.0.0.jar', was " + filePath;
        File file = new File(m_directory, filePath);
        assert file.exists();
    }

    @Test()
    public void putBundleSameDuplicate() throws Exception {
        File bundle = createTmpResource("foo.bar", "1.0.0");
        String filePath = m_bundleStore.put(new FileInputStream(bundle), null, false);
        assert filePath != null;
        String filePath2 = m_bundleStore.put(new FileInputStream(bundle), null, false);
        assert filePath2 != null;
        assert filePath2.equals(filePath);
    }

    @Test()
    public void putBundleDifferentDuplicate() throws Exception {
        File bundle = createTmpResource("foo.bar", "1.0.0", new byte[] { 1 });
        File bundle2 = createTmpResource("foo.bar", "1.0.0", new byte[] { 2 });
        String filePath = m_bundleStore.put(new FileInputStream(bundle), null, false);
        assert filePath != null;
        String filePath2 = m_bundleStore.put(new FileInputStream(bundle2), null, false);
        assert filePath2 == null;
    }

    @Test(expectedExceptions = { IOException.class }, expectedExceptionsMessageRegExp = "Not a valid bundle and no filename found.*")
    public void putBundleFail() throws Exception {
        File bundle = createTmpResource(null, "1.0.0");
        String filePath = m_bundleStore.put(new FileInputStream(bundle), null, false);
        assert filePath.equals("foo/bar/foo.bar-1.0.0.jar") : "Path should be 'foo/bar/foo.bar-1.0.0.jar', was " + filePath;
        File file = new File(m_directory, filePath);
        assert file.exists();
    }

    @Test()
    public void putRemoveArtifact() throws Exception {
        File bundle = createTmpResource(null, null);
        String filePath = m_bundleStore.put(new FileInputStream(bundle), "foo.bar-2.3.7.test1.xxx", false);
        assert filePath.equals("foo/foo.bar-2.3.7.test1.xxx");
        File file = new File(m_directory, filePath);
        assert file.exists();
    }

    @Test()
    public void putArtifactDefaultVersion() throws Exception {
        File bundle = createTmpResource(null, null);
        String filePath = m_bundleStore.put(new FileInputStream(bundle), "foo.bar.xxx", false);
        assert filePath.equals("foo/foo.bar.xxx");
        File file = new File(m_directory, filePath);
        assert file.exists();
    }

    @Test()
    public void putArtifactMavenVersion() throws Exception {
        File bundle = createTmpResource(null, null);
        String filePath = m_bundleStore.put(new FileInputStream(bundle), "foo.bar-2.3.7-test1.xxx", false);
        assert filePath.equals("foo/foo.bar-2.3.7-test1.xxx");
        File file = new File(m_directory, filePath);
        assert file.exists();
    }

    @Test(expectedExceptions = { IOException.class }, expectedExceptionsMessageRegExp = "Not a valid bundle and no filename found.*")
    public void putArtifactFail1() throws Exception {
        File bundle = createTmpResource(null, null);
        m_bundleStore.put(new FileInputStream(bundle), null, false);
    }

    @Test(expectedExceptions = { IOException.class }, expectedExceptionsMessageRegExp = "Not a valid bundle and no filename found.*")
    public void putArtifactFail2() throws Exception {
        File bundle = createTmpResource(null, null);
        m_bundleStore.put(new FileInputStream(bundle), "", false);
    }

    @Test()
    public void removeBundle() throws Exception {
        File bundle = createTmpResource("foo.bar", "1.0.0");
        String filePath = m_bundleStore.put(new FileInputStream(bundle), null, false);
        File file = new File(m_directory, filePath);
        assert file.exists();
        assert m_bundleStore.remove(filePath);
        assert !file.exists();
    }

    @Test()
    public void removeBundleFaill() throws Exception {
        File file = new File(m_directory, "no/such/file");
        assert !file.exists();
        assert !m_bundleStore.remove("no/such/file");
    }

    @Test()
    public void removeArtifact() throws Exception {
        File bundle = createTmpResource(null, null);
        String filePath = m_bundleStore.put(new FileInputStream(bundle), "foo.bar-2.3.7.test1.xxx", false);
        assert filePath.equals("foo/foo.bar-2.3.7.test1.xxx");
        File file = new File(m_directory, filePath);
        assert file.exists();
        assert m_bundleStore.remove("foo/foo.bar-2.3.7.test1.xxx");
        assert !file.exists();
    }

    /**
     * Test whether not configuring the directory (so retrieving the directory returns null), results in a
     * ConfigurationException. Updating with null as dictionary should only clean up things, and nothing else.
     */
    @Test()
    public void updateConfigurationWithNull() throws Exception {
        boolean exceptionThrown = false;

        Dictionary<String, Object> props = new Hashtable<>();

        try {
            m_bundleStore.updated(props);
        }
        catch (ConfigurationException e) {
            exceptionThrown = true;
        }
        assert exceptionThrown : "Reconfiguring directory succeeded but should fail, as property is supposed to be missing";
        assert !m_metadata.generated() : "After changing the directory, the metadata should not be regenerated.";

        exceptionThrown = false;
        try {
            m_bundleStore.updated(null);
        }
        catch (ConfigurationException e) {
            exceptionThrown = true;
        }
        assert !exceptionThrown : "Reconfiguring succeeded as the bundle should only do the clean up, and not throw exception";
        assert !m_metadata.generated() : "After changing the directory, the metadata should not be regenerated.";
    }

    /**
     * Test whether not configuring the directory (so retrieving the directory returns null), results in a
     * ConfigurationException.
     */
    @Test()
    public void updateConfigurationWithSameDirectory() throws Exception {

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, m_directory.getAbsolutePath());
        try {
            m_bundleStore.updated(props);
        }
        catch (ConfigurationException e) {
            assert false : "Nothing should happen, as the directory did not change";
        }
        assert !m_metadata.generated() : "After changing the directory, the metadata should not be regenerated.";
    }

    private File createTmpResource(String symbolicName, String version) throws IOException {
        return createTmpResource(symbolicName, version, null);
    }

    private File createTmpResource(String symbolicName, String version, byte[] data) throws IOException {
        File tmpFile = File.createTempFile("tmpbundle-", "jar");
        tmpFile.deleteOnExit();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (symbolicName != null) {
            manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
        }
        if (version != null) {
            manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, version);
        }
        JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpFile), manifest);
        if (data != null) {
            target.putNextEntry(new ZipEntry("data"));
            target.write(data, 0, data.length);
        }
        target.close();
        return tmpFile;
    }

    private File createFileWithContent(File baseDir, String filename, int size) throws IOException {
        OutputStream fileOut = null;
        File file = new File(baseDir, filename);
        try {
            fileOut = new FileOutputStream(file);
            byte[] byteArray = new byte[size];
            Random randomContentCreator = new Random();
            randomContentCreator.nextBytes(byteArray);
            fileOut.write(byteArray);
            return file;
        }
        finally {
            try {
                if (fileOut != null) {
                    fileOut.close();
                }
            }
            catch (IOException e) {
                throw e;
            }
        }
    }
}
