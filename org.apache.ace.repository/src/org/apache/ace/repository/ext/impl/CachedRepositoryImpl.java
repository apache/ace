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
package org.apache.ace.repository.ext.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Provides a CachedRepository, which uses either a <code>Repository</code> and a <code>BackupRepository</code>
 * as remote and local storage, or a URL location and two files, from which it will create a <code>Repository</code>
 *  and a <code>FileBasedBackupRepository</code>. Note that this class is not thread-safe, and should be synchronized
 *  by the caller.
 */
@ConsumerType
public class CachedRepositoryImpl implements CachedRepository {
    public static final long UNCOMMITTED_VERSION = -1;

    private volatile long m_mostRecentVersion;

    private final BackupRepository m_local;
    private final Repository m_remote;

    /**
     * Creates a cached repository using.
     *
     * @param remote A repository which holds committed versions.
     * @param backup A backup repository for local changes.
     * @param mostRecentVersion The version from which <code>backup</code> was checked out or committed.
     * If no version has been committed yet, use <code>UNCOMMITTED_VERSION</code>.
     */
    public CachedRepositoryImpl(Repository remote, BackupRepository backup, long mostRecentVersion) {
        m_remote = remote;
        m_local = backup;
        m_mostRecentVersion = mostRecentVersion;
    }

    public InputStream checkout(boolean fail) throws IOException, IllegalArgumentException {
        m_mostRecentVersion = highestRemoteVersion();
        if (m_mostRecentVersion <= 0) {
            // If there is no remote version, then simply return an empty stream.
            if (fail) {
                throw new IOException("No version has yet been checked in to the repository.");
            }
            return null;
        }
        return checkout(m_mostRecentVersion);
    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        m_local.write(m_remote.checkout(version));
        m_local.backup();

        m_mostRecentVersion = version;

        return m_local.read();
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        m_local.write(data);

        return commit(fromVersion);
    }

    public boolean commit() throws IOException {
        if (m_mostRecentVersion < 0) {
            throw new IllegalStateException("A commit should be preceded by a checkout.");
        }
        boolean result = commit(m_mostRecentVersion);
        if (result) {
            // ACE-421: only bump in case of successful commit!
            m_mostRecentVersion++;
        }
        return result;
    }

    public boolean commit(long fromVersion) throws IOException, IllegalArgumentException {
        boolean success = m_remote.commit(m_local.read(), fromVersion);
        if (success) {
            m_local.backup();
        }

        return success;
    }

    public SortedRangeSet getRange() throws IOException {
        return m_remote.getRange();
    }

    public InputStream getLocal(boolean fail) throws IllegalArgumentException, IOException {
        // ACE-240: only fail in case there is no local version available; when mostRecentVersion
        // equals to 0, it means that nothing has been committed locally...
        if (m_mostRecentVersion <= 0 && fail) {
    		throw new IOException("No local version available of " + m_local + ", remote " + m_remote);
        }
        return m_local.read();
    }

    public boolean revert() throws IOException {
         return m_local.restore();
    }

    public void writeLocal(InputStream data) throws IllegalArgumentException, IOException {
        m_local.write(data);
    }

    public long getMostRecentVersion() {
        return m_mostRecentVersion;
    }

    public boolean isCurrent() throws IOException {
        return highestRemoteVersion() == m_mostRecentVersion;
    }

    public void deleteLocal() throws IOException {
    	m_local.delete();
    }

    private long highestRemoteVersion() throws IOException {
        long result = 0;
        RangeIterator ri = getRange().iterator();
        while (ri.hasNext()) {
            result = ri.next();
        }
        return result;
    }
}