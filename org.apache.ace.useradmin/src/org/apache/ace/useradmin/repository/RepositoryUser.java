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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ace.repository.ext.CachedRepository;
import org.osgi.service.useradmin.User;

/**
 * Wrapper for {@link User} that prevents changes to the user when the store is out of sync with the main repository
 */
public class RepositoryUser implements User {

    private User m_delegate;
    private CachedRepository m_cachedRepository;
    private AtomicLong m_version;

    public RepositoryUser(User user, CachedRepository cachedRepository, AtomicLong version) {
        m_delegate = user;
        m_cachedRepository = cachedRepository;
        m_version = version;
    }

    @Override
    public String getName() {
        return m_delegate.getName();
    }

    @Override
    public int getType() {
        return m_delegate.getType();
    }

    @Override
    public Dictionary getProperties() {
        return new RepoProperties(m_delegate.getProperties());
    }

    @Override
    public Dictionary getCredentials() {
        return new RepoProperties(m_delegate.getCredentials());
    }

    @Override
    public boolean hasCredential(String key, Object value) {
        return m_delegate.hasCredential(key, value);
    }

    @SuppressWarnings("rawtypes")
    private class RepoProperties extends Dictionary {

        private Dictionary m_delegate;

        public RepoProperties(Dictionary dictionary) {
            this.m_delegate = dictionary;

        }

        @Override
        public int size() {
            return m_delegate.size();
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
        public Enumeration elements() {
            return m_delegate.elements();
        }

        @Override
        public Object get(Object key) {
            return m_delegate.get(key);
        }

        @SuppressWarnings("unchecked")
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

    }

    protected void checkRepoUpToDate() {
        try {
            if (!m_cachedRepository.isCurrent()) {
                throw new IllegalStateException("Repository out of date, refresh first");
            }
            if (m_version.get() != m_cachedRepository.getMostRecentVersion()) {
                throw new IllegalStateException("User out of date, refresh first");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
