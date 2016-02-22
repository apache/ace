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
package org.apache.ace.useradmin.repository;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.useradmin.User;

/**
 * Wrapper for {@link User} that prevents changes to the user when the store is out of sync with the main repository
 */
public class RepositoryUser implements User {
    @SuppressWarnings("rawtypes")
    private class RepoProperties extends Dictionary {
        private Dictionary<Object, Object> m_delegate;

        public RepoProperties(Dictionary<Object, Object> dictionary) {
            m_delegate = dictionary;
        }

        @Override
        public Enumeration elements() {
            return m_delegate.elements();
        }

        @Override
        public Object get(Object key) {
            return m_delegate.get(key);
        }

        @Override
        public boolean isEmpty() {
            return m_delegate.isEmpty();
        }

        @Override
        public Enumeration keys() {
            return m_delegate.keys();
        }

        @Override
        public Object put(Object key, Object value) {
            checkRepoUpToDate();
            return m_delegate.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            checkRepoUpToDate();
            return m_delegate.remove(key);
        }

        @Override
        public int size() {
            return m_delegate.size();
        }
    }
    private final RepoCurrentChecker m_repoCurrentChecker;
    protected final User m_delegate;

    protected final AtomicLong m_version;

    public RepositoryUser(User user, AtomicLong version, RepoCurrentChecker repoCurrentChecker) {
        m_delegate = user;
        m_version = version;
        m_repoCurrentChecker = repoCurrentChecker;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RepositoryUser other = (RepositoryUser) obj;
        if (!m_delegate.equals(other.m_delegate)) {
            return false;
        }
        if (!m_version.equals(other.m_version)) {
            return false;
        }
        return true;
    }

    @Override
    public Dictionary getCredentials() {
        return new RepoProperties(m_delegate.getCredentials());
    }

    @Override
    public String getName() {
        return m_delegate.getName();
    }

    @Override
    public Dictionary getProperties() {
        return new RepoProperties(m_delegate.getProperties());
    }

    @Override
    public int getType() {
        return m_delegate.getType();
    }

    @Override
    public boolean hasCredential(String key, Object value) {
        return m_delegate.hasCredential(key, value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((m_delegate == null) ? 0 : m_delegate.hashCode());
        result = prime * result + ((m_version == null) ? 0 : m_version.hashCode());
        return result;
    }

    protected final void checkRepoUpToDate() {
        m_repoCurrentChecker.checkRepoUpToDate(this, m_version);
    }
}
