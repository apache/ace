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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RepositoryImplTest {

    private RepositoryImpl m_repo;
    private File m_baseDir;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws IOException {
        m_baseDir = File.createTempFile("repo", null);
        m_baseDir.delete();
        m_baseDir.mkdirs();
        m_repo = new RepositoryImpl(new File(m_baseDir, "data"), new File(m_baseDir, "tmp"), true);
    }

    @Test(groups = { UNIT })
    public void testGetAndPut() throws Exception {
        InputStream data = new ByteArrayInputStream("abc".getBytes());
        boolean result = m_repo.put(data, 1);
        assert result : "Put should have succeeded.";

        File file = new File(m_baseDir, "data" + File.separator + "1");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        assert "abc".equals(reader.readLine()) : "File " + file.getAbsolutePath() + " should have contained 'abc'.";

        assert !m_repo.put(data, 1) : "Putting an existing version should return false.";

        InputStream in = m_repo.get(1);
        reader = new BufferedReader(new InputStreamReader(in));
        assert "abc".equals(reader.readLine()) : "'get'ting version 1 should have returned an inputstream containing 'abc'";
        assert null == m_repo.get(2) : "'get'ting a non-existing version should return null";
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testPutNegative() throws Exception {
        m_repo.put(new ByteArrayInputStream("abc".getBytes()), -1);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testPutZero() throws Exception {
        m_repo.put(new ByteArrayInputStream("abc".getBytes()), 0);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testGetNegative() throws Exception {
        m_repo.get(-1);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testGetZero() throws Exception {
        m_repo.get(0);
    }

    @Test(groups = { UNIT })
    public void testCommitAndCheckout() throws Exception {
        InputStream data = new ByteArrayInputStream("abc".getBytes());
        boolean result = m_repo.commit(data, 1);
        assert !result : "Commit with incorrect 'base' number should have failed.";

        result = m_repo.commit(data, 0);
        assert result : "Commit should have succeeded";

        File file = new File(m_baseDir, "data" + File.separator + "1");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        assert "abc".equals(reader.readLine()) : "File " + file.getAbsolutePath() + " should have contained 'abc'.";

        assert !m_repo.commit(data, 0) : "Committing an existing version should return false.";
        assert !m_repo.commit(data, 999) : "Committing should only succeed if the base number equals the highest version inside the repository";

        InputStream in = m_repo.checkout(1);
        reader = new BufferedReader(new InputStreamReader(in));
        assert "abc".equals(reader.readLine()) : "Checking out version 1 should have returned an inputstream containing 'abc'";
        assert null == m_repo.get(2) : "Checking out a non-existing version should return null";
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testCommitNegative() throws Exception {
        m_repo.commit(new ByteArrayInputStream("abc".getBytes()), -1);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testCheckoutNegative() throws Exception {
        m_repo.checkout(-1);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalArgumentException.class})
    public void testCheckoutZero() throws Exception {
        m_repo.checkout(0);
    }

    @Test(groups = { UNIT }, expectedExceptions = {IllegalStateException.class})
    public void testUpdated() throws Exception {
        m_repo.updated(false);
        assert !m_repo.commit(new ByteArrayInputStream("abc".getBytes()), 0) : "Committing should not be allowed on slave repositories.";
        assert m_repo.put(new ByteArrayInputStream("abc".getBytes()), 1) : "'put'ting a replica should be allowed on slave repositories.";
        File file = new File(m_baseDir, "newLocation" + File.separator + "1");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        assert "abc".equals(reader.readLine()) : "File " + file.getAbsolutePath() + " should have contained 'abc'.";
    }

}