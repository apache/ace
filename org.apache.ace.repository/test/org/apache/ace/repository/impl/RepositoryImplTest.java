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
package org.apache.ace.repository.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.ace.range.SortedRangeSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RepositoryImplTest {

    private File m_baseDir;

    /**
     * Tests that if we do change something in an {@link InputStream} while committing data, that the version is bumped
     * for a repository.
     */
    @Test()
    public void testCheckoutAndCommitWithChangeDoesChangeVersion() throws Exception {
        SortedRangeSet range;
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        InputStream data = new ByteArrayInputStream("abc".getBytes());

        assertTrue(repo.put(data, 1), "Put should have succeeded");

        range = repo.getRange();
        assertEquals(1, range.getHigh(), "Version 1 should be the most recent one");

        InputStream is = repo.checkout(1);
        assertNotNull(is, "Nothing checked out?!");

        data = new ByteArrayInputStream("def".getBytes());

        assertTrue(repo.commit(data, 1), "Commit should NOT be ignored");

        range = repo.getRange();
        assertEquals(2, range.getHigh());
    }

    /**
     * Tests that if we do not change anything in an {@link InputStream} while committing data, that the version is not
     * bumped for a repository.
     */
    @Test()
    public void testCheckoutAndCommitWithoutChangeDoesNotChangeVersion() throws Exception {
        SortedRangeSet range;
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        InputStream data = new ByteArrayInputStream("abc".getBytes());

        assertTrue(repo.put(data, 1), "Put should have succeeded");

        range = repo.getRange();
        assertEquals(1, range.getHigh(), "Version 1 should be the most recent one");

        InputStream is = repo.checkout(1);
        assertFalse(repo.commit(is, 1), "Commit should be ignored");

        range = repo.getRange();
        assertEquals(1, range.getHigh(), "Version 1 should still be the most recent one");
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testCheckoutNegativeVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.checkout(-1);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testCheckoutVersionZeroOnEmptyRepositoryFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.checkout(0);
    }

    @Test()
    public void testCommitAndCheckout() throws Exception {
        String readLine;
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        File file = new File(m_baseDir, "data" + File.separator + "1");

        InputStream data = new ByteArrayInputStream("abc".getBytes());
        assertTrue(repo.commit(data, 0), "Commit should have succeeded");

        readLine = getContentAsString(file);
        assertEquals(readLine, "abc", "File " + file.getAbsolutePath() + " should have contained 'abc'.");

        readLine = getContentAsString(repo.checkout(1));
        assertEquals(readLine, "abc", "Checking out version 1 should have returned an inputstream containing 'abc'");
        assertNull(repo.get(2), "Checking out a non-existing version should return null");
    }

    @Test(expectedExceptions = { IOException.class })
    public void testCommitExistingVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);

        InputStream data = new ByteArrayInputStream("abc".getBytes());
        repo.commit(data, 0);

        data = new ByteArrayInputStream("def".getBytes());
        repo.commit(data, 0); // should fail, as we're at version 1!
    }

    @Test(expectedExceptions = { IOException.class })
    public void testCommitIncorrectVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        InputStream data = new ByteArrayInputStream("abc".getBytes());

        repo.commit(data, 1); // should fail, as we're at version 0!
    }

    /**
     * Tests that if we do change something in an {@link InputStream} while committing data, that the version is bumped
     * for a repository.
     */
    @Test()
    public void testCommitInitialVersionDoesChangeVersion() throws Exception {
        SortedRangeSet range;
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        InputStream data = new ByteArrayInputStream("abc".getBytes());

        range = repo.getRange();
        assertEquals(0, range.getHigh(), "Version 0 should be the most recent one");

        assertTrue(repo.commit(data, 0), "Commit should NOT be ignored");

        range = repo.getRange();
        assertEquals(1, range.getHigh());
    }

    @Test()
    public void testCommitMultipleVersionsOk() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);

        assertTrue(repo.commit(new ByteArrayInputStream("abc-1".getBytes()), 0), "Commit should have worked.");
        assertTrue(repo.commit(new ByteArrayInputStream("abc-2".getBytes()), 1), "Commit should have worked.");
        assertTrue(repo.commit(new ByteArrayInputStream("abc-3".getBytes()), 2), "Commit should have worked.");

        SortedRangeSet range = repo.getRange();
        assertTrue(range.getHigh() == 3, "We should have 3 versions in the repository.");
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testCommitNegativeVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.commit(new ByteArrayInputStream("abc".getBytes()), -1);
    }

    @Test(expectedExceptions = { IOException.class })
    public void testCommitNonExistingVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);

        InputStream data = new ByteArrayInputStream("abc".getBytes());
        repo.commit(data, 0);

        data = new ByteArrayInputStream("def".getBytes());
        repo.commit(data, 2); // should fail, as we're at version 1!
    }

    @Test()
    public void testCommitToLimitedRepository() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true, 2 /* limit */);

        assertTrue(repo.commit(new ByteArrayInputStream("abc-1".getBytes()), 0), "Commit should have worked.");
        assertTrue(repo.commit(new ByteArrayInputStream("abc-2".getBytes()), 1), "Commit should have worked.");
        assertTrue(repo.commit(new ByteArrayInputStream("abc-3".getBytes()), 2), "Commit should have worked.");

        assertNotNull(repo.checkout(3));
        assertNotNull(repo.checkout(2));
        assertNull(repo.checkout(1));

        repo.updated(true, 3);

        assertTrue(repo.commit(new ByteArrayInputStream("abc-4".getBytes()), 3), "Commit should have worked.");
        assertNotNull(repo.checkout(2));

        repo.updated(true, 1);

        assertNull(repo.checkout(2));
        assertNull(repo.checkout(3));
        assertNotNull(repo.checkout(4));
    }

    @Test()
    public void testCustomFileExtensionOk() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), ".gz", true);
        File file = new File(m_baseDir, "data" + File.separator + "1.gz");

        InputStream data = new ByteArrayInputStream("abc".getBytes());
        assertTrue(repo.put(data, 1), "Put should have succeeded.");

        String readLine = getContentAsString(file);
        assertEquals(readLine, "abc", "File " + file.getAbsolutePath() + " should have contained 'abc'.");
    }

    @Test()
    public void testGetAndPut() throws Exception {
        String readLine;
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        File file = new File(m_baseDir, "data" + File.separator + "1");

        InputStream data = new ByteArrayInputStream("abc".getBytes());
        assertTrue(repo.put(data, 1), "Put should have succeeded.");

        readLine = getContentAsString(file);
        assertEquals(readLine, "abc", "File " + file.getAbsolutePath() + " should have contained 'abc'.");

        assertFalse(repo.put(data, 1), "Putting an existing version should return false.");

        readLine = getContentAsString(repo.get(1));
        assertEquals(readLine, "abc", "'get'ting version 1 should have returned an inputstream containing 'abc'");

        assertNull(repo.get(2), "'get'ting a non-existing version should return null");
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testGetNegativeVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.get(-1);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testGetVersionZeroForEmptyRepositoryFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.get(0);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testPutNegativeVersionFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.put(new ByteArrayInputStream("abc".getBytes()), -1);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testPutVersionZeroFail() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        repo.put(new ByteArrayInputStream("abc".getBytes()), 0);
    }

    @Test(expectedExceptions = { IllegalStateException.class })
    public void testUpdatedConfigurationOk() throws Exception {
        RepositoryImpl repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
        File file = new File(m_baseDir, "newLocation" + File.separator + "1");

        // update the configuration of the repository...
        repo.updated(false /* isMaster */, Long.MAX_VALUE);

        assertFalse(repo.commit(new ByteArrayInputStream("abc".getBytes()), 0), "Committing should not be allowed on slave repositories.");
        assertTrue(repo.put(new ByteArrayInputStream("abc".getBytes()), 1), "'put'ting a replica should be allowed on slave repositories.");

        String readLine = getContentAsString(file);
        assertEquals(readLine, "abc", "File " + file.getAbsolutePath() + " should have contained 'abc'.");
    }

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws IOException {
        m_baseDir = File.createTempFile("repo", null);
        m_baseDir.delete();
        m_baseDir.mkdirs();
    }

    private String getContentAsString(File file) throws IOException {
        return getContentAsString(new FileInputStream(file));
    }

    private String getContentAsString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            return reader.readLine();
        }
        finally {
            is.close();
            reader.close();
        }
    }
}
