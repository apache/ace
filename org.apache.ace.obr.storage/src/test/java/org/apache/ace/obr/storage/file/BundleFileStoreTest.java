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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.storage.BundleStore;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.cm.ConfigurationException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BundleFileStoreTest {

    private BundleStore m_bundleStore;
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

        Properties props = new Properties();
        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, m_directory.getAbsolutePath());
        m_bundleStore.updated(props);

        // create a mock MetadataGenerator
        m_metadata = new MockMetadataGenerator();
        TestUtils.configureObject(m_bundleStore, MetadataGenerator.class, m_metadata);

        // create some bundles to work with
        m_bundleSubstitute1 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub1.jar", 1000);
        m_bundleSubstitute2 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub2.jar", 2000);
        m_bundleSubstitute3 = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub3.jar", 3000);
        m_bundleRepositoryFile = createFileWithContent(m_directory.getAbsoluteFile(), "repository.xml", 1000);
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
    @Test(groups = { UNIT })
    public void getBundle() throws Exception {
        m_bundleStore.get(m_bundleSubstitute1.getName());
        assert !m_metadata.generated() : "During getting a bundle, the metadata should not be regenerated.";
    }

    /**
     * Test whether the metadata is generated when getting a bundle from the repository.
     */
    @Test(groups = { UNIT })
    public void getUnexistingBundle() throws Exception {
        try {
            m_bundleStore.get("blaat");
        }
        catch (IOException e) {
            // exception is expected
            return;
        }
        assert false : "Getting an unexisting file did not result in an exception";
    }

    /**
     * Test whether retrieving the repository.xml results in a call to the (mock) metadata generator,
     * and the original file should correspond with the retrieved file.
     */
    @Test(groups = { UNIT })
    public void getRepositoryFile() throws Exception {
        InputStream newInputStream = m_bundleStore.get("repository.xml");
        assert m_metadata.generated() : "During getting the repository file, the metadata should be regenerated.";

        byte[] orgContentBuffer = new byte[1000];
        newInputStream.read(orgContentBuffer);

        FileInputStream orgInputStream = new FileInputStream(m_bundleRepositoryFile);
        byte[] newContentBuffer = new byte[1000];
        orgInputStream.read(newContentBuffer);

        assert Arrays.equals(orgContentBuffer, newContentBuffer) : "The original repository.xml content should equal the newly retrieved content.";
    }

    /**
     * Test whether the BundleStore notices the set of bundles has changed (bundle updated),
     * and makes a call to the (mock) metadata generator.
     */
    @Test(groups = { UNIT })
    public void updateBundle() throws Exception {
        m_bundleStore.get("repository.xml");
        assert m_metadata.numberOfCalls() == 1 : "The MetadataGenerator should be called once";

        m_bundleSubstitute1Larger = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSub1.jar", 2000);

        m_bundleStore.get("repository.xml");
        assert m_metadata.numberOfCalls() == 2 : "The MetadataGenerator should be called twice";

        // test specific tear down
        m_bundleSubstitute1Larger.delete();
    }

    /**
     * Test whether the BundleStore notices the set of bundles has changed (bundle added),
     * and makes a call to the (mock) metadata generator. Also a call should be made when
     * a bundle is replaced by another one (number of bundles stay the same, but one bundle
     * is replaced by another).
     */
    @Test(groups = { UNIT })
    public void addBundle() throws Exception {
        m_bundleStore.get("repository.xml");
        assert m_metadata.numberOfCalls() == 1 : "The MetadataGenerator should be called once";

        File bundleSubstituteX = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSubX.jar", 2000);

        m_bundleStore.get("repository.xml");
        assert m_metadata.numberOfCalls() == 2 : "The MetadataGenerator should be called twice";

        bundleSubstituteX.delete();

        File bundleSubstituteY = createFileWithContent(m_directory.getAbsoluteFile(), "bundleSubY.jar", 2000);

        m_bundleStore.get("repository.xml");
        assert m_metadata.numberOfCalls() == 3 : "The MetadataGenerator should be called three times";

        // test specific tear down
        bundleSubstituteY.delete();
    }

    /**
     * Test whether the BundleStore notices the set of bundles has not changed, and thus
     * will not make a call to the (mock) metadata generator.
     */
    @Test(groups = { UNIT })
    public void replaceWithSameBundle() throws Exception {
        m_bundleStore.get("bundleSub1.jar");
        assert m_metadata.numberOfCalls() == 0 : "The MetadataGenerator should not be called";

        FileInputStream inputStream = new FileInputStream(m_bundleSubstitute1);
        byte[] buffer = new byte[1000];
        inputStream.read(buffer);
        m_bundleSubstitute1.delete();

        File newFile = new File(m_directory, "bundleSub1.jar");
        FileOutputStream outputStream = new FileOutputStream(newFile);

        outputStream.write(buffer);
        outputStream.close();

        m_bundleStore.get("bundleSub1.jar");
        assert m_metadata.numberOfCalls() == 0 : "The MetadataGenerator should still not be called";
    }

    /**
     * Test whether changing the directory where the bundles are stored, does not result in a call
     * to the (mock) metadata generator, as the metadata will only be regenerated after getting
     * a file.
     */
    @Test(groups = { UNIT })
    public void updateConfigurationWithValidConfiguration() throws Exception {
        File subDir = new File(m_directory.getAbsolutePath(), "changedDirectory");
        subDir.mkdir();

        Properties props = new Properties();
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
     * Test whether changing the directory where the bundles are stored to something that is not
     * a directory, this should fail.
     */
    @Test(groups = { UNIT })
    public void updateConfigurationWithIsNotDirectory() throws Exception {
        boolean exceptionThrown = false;

        File file = new File(m_directory.getAbsolutePath(), "file");
        file.createNewFile();

        Properties props = new Properties();
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

    @Test(groups = { UNIT })
    public void putBundle() throws Exception {
        m_bundleStore.put("filename", new InputStream() {
            private int i = 0;

            @Override
            public int read() throws IOException {
                if (i < 1) {
                    i++;
                    return 'a';
                }
                else {
                    return -1;
                }
            }
        });
        File file = new File(m_directory, "filename");
        FileInputStream input = new FileInputStream(file);
        assert input.read() == 'a';
        assert input.read() == -1;
        input.close();
    }

    @Test(groups = { UNIT })
    public void removeExistingBundle() throws Exception {
        m_bundleStore.put("filename", new InputStream() {
            private int i = 0;

            @Override
            public int read() throws IOException {
                if (i < 1) {
                    i++;
                    return 'a';
                }
                else {
                    return -1;
                }
            }
        });
        File file = new File(m_directory, "filename");
        assert file.exists();
        m_bundleStore.remove("filename");
        assert !file.exists();
    }

    /**
     * Test whether not configuring the directory (so retrieving the directory returns null),
     * results in a ConfigurationException. Updating with null as dictionary should only clean up
     * things, and nothing else.
     */
    @Test(groups = { UNIT })
    public void updateConfigurationWithNull() throws Exception {
        boolean exceptionThrown = false;

        Properties props = new Properties();
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
     * Test whether not configuring the directory (so retrieving the directory returns null),
     * results in a ConfigurationException.
     */
    @Test(groups = { UNIT })
    public void updateConfigurationWithSameDirectory() throws Exception {

        Properties props = new Properties();
        props.put(OBRFileStoreConstants.FILE_LOCATION_KEY, m_directory.getAbsolutePath());
        try {
            m_bundleStore.updated(props);
        }
        catch (ConfigurationException e) {
            assert false : "Nothing should happen, as the directory did not change";
        }
        assert !m_metadata.generated() : "After changing the directory, the metadata should not be regenerated.";
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
