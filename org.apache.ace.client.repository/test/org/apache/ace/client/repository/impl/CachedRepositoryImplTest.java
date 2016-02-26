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
package org.apache.ace.client.repository.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.ext.impl.CachedRepositoryImpl;
import org.testng.annotations.Test;

public class CachedRepositoryImplTest {

    /**
     * Initial checkout: the remote repository contains some data for a given version, we make the cached repository do
     * a checkout, and check whether all data arrives at the right places: in getLocal, and in the backup repository.
     */
    @Test()
    public void testInitialCheckout() throws IllegalArgumentException, IOException {
        Repository m_repository = new MockRepository();
        byte[] testContent = new byte[] { 'i', 'n', 'i', 't', 'i', 'a', 'l' };
        m_repository.commit(new ByteArrayInputStream(testContent), 0);
        BackupRepository m_backupRepository = new MockBackupRepository();

        CachedRepository m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);

        InputStream input = m_cachedRepository.checkout(1);
        byte[] inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from checkout: " + new String(inputBytes);
        input = m_cachedRepository.getLocal(false);
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from getLocal: " + new String(inputBytes);
        input = m_backupRepository.read();
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from the backup repository: " + new String(inputBytes);
    }

    /**
     * There are two types of commit, one that takes an input stream, and one that simply flushes whatever is in the
     * current to the remote repository. After each commit, we should be able to renew the cached repository, and
     * checkout the data we put in before.
     */
    @Test()
    public void testCommit() throws IllegalArgumentException, IOException {
        Repository m_repository = new MockRepository();
        BackupRepository m_backupRepository = new MockBackupRepository();

        CachedRepository m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);
        byte[] testContent = new byte[] { 'i', 'n', 'i', 't', 'i', 'a', 'l' };

        InputStream input = new ByteArrayInputStream(testContent);
        m_cachedRepository.commit(input, 0);

        m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);
        input = m_cachedRepository.checkout(1);
        byte[] inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from checkout: " + new String(inputBytes);

        byte[] newTestContent = new byte[] { 'n', 'e', 'w' };

        m_cachedRepository.writeLocal(new ByteArrayInputStream(newTestContent));

        m_cachedRepository.commit();

        m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);
        input = m_cachedRepository.checkout(2);
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, newTestContent) : "We got something different than 'new' from checkout: " + new String(inputBytes);
    }

    /**
     * After checking out and changing stuff, we want to revert to the old version.
     * 
     * @throws IOException
     * @throws IllegalArgumentException
     */
    @Test()
    public void testRevert() throws IllegalArgumentException, IOException {
        Repository m_repository = new MockRepository();
        BackupRepository m_backupRepository = new MockBackupRepository();

        CachedRepository m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);
        byte[] testContent = new byte[] { 'i', 'n', 'i', 't', 'i', 'a', 'l' };

        InputStream input = new ByteArrayInputStream(testContent);
        m_cachedRepository.commit(input, 0);

        m_cachedRepository = new CachedRepositoryImpl(m_repository, m_backupRepository, 0);
        input = m_cachedRepository.checkout(1);

        byte[] newTestContent = new byte[] { 'n', 'e', 'w' };

        m_cachedRepository.writeLocal(new ByteArrayInputStream(newTestContent));
        input = m_cachedRepository.getLocal(false);
        byte[] inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, newTestContent) : "We got something different than 'new' from getLocal: " + new String(inputBytes);

        m_cachedRepository.revert();
        input = m_cachedRepository.getLocal(false);
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from getLocal: " + new String(inputBytes);
    }
}
