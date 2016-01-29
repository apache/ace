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

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.osgi.service.useradmin.User;

@SuppressWarnings({ "unchecked" })
public class RepositoryAdminLoginContextImpl implements RepositoryAdminLoginContext {

    private final String m_sessionid;
    private final User m_user;
    private final List<RepositorySetDescriptor> m_descriptors = new ArrayList<>();

    RepositoryAdminLoginContextImpl(User user, String sessionid) {
        m_user = user;
        m_sessionid = sessionid;
    }

    /**
     * {@inheritDoc}
     */
    public RepositoryAdminLoginContext add(BaseRepositoryContext<?> repositoryContext) {
        if (!(repositoryContext instanceof AbstractRepositoryContext)) {
            throw new IllegalArgumentException("Invalid repository context!");
        }

        addDescriptor(((AbstractRepositoryContext<?>) repositoryContext).createDescriptor());

        return this;
    }

    /**
     * @param descriptor
     *            the descriptor to add, cannot be <code>null</code>.
     */
    public void addDescriptor(RepositorySetDescriptor descriptor) {
        checkConsistency(descriptor);

        synchronized (m_descriptors) {
            m_descriptors.add(descriptor);
        }
    }

    /**
     * {@inheritDoc}
     */
    public ShopRepositoryContext createShopRepositoryContext() {
        return new ShopRepositoryContextImpl();
    }

    /**
     * {@inheritDoc}
     */
    public TargetRepositoryContext createTargetRepositoryContext() {
        return new TargetRepositoryContextImpl();
    }

    /**
     * {@inheritDoc}
     */
    public DeploymentRepositoryContext createDeploymentRepositoryContext() {
        return new DeploymentRepositoryContextImpl();
    }

    /**
     * @return a list with all repository set descriptors, never <code>null</code>.
     */
    public List<RepositorySetDescriptor> getDescriptors() {
        List<RepositorySetDescriptor> result;
        synchronized (m_descriptors) {
            result = new ArrayList<>(m_descriptors);
        }
        return result;
    }

    User getUser() {
        return m_user;
    }

    String getSessionId() {
        return m_sessionid;
    }

    /**
     * Checks the consistency of the internal descriptors with the one given.
     * 
     * @param descriptor
     *            the to-be-added repository set descriptor, cannot be <code>null</code>.
     */
    private void checkConsistency(RepositorySetDescriptor descriptor) {
        List<Class<? extends ObjectRepository<?>>> seenClasses = new ArrayList<>();
        List<String> seenNames = new ArrayList<>();

        // Presumption: initially we start out without any duplication...
        for (RepositorySetDescriptor rsd : getDescriptors()) {
            seenClasses.addAll(Arrays.asList(rsd.m_objectRepositories));
            seenNames.add(rsd.m_name);
        }

        if (seenNames.contains(descriptor.m_name)) {
            throw new IllegalArgumentException("Duplicate repository name!");
        }

        for (Class<? extends ObjectRepository<?>> clazz : descriptor.m_objectRepositories) {
            if (seenClasses.contains(clazz)) {
                throw new IllegalArgumentException("Duplicate object repository!");
            }
        }
    }

    /**
     * Helper class to store all relevant information about a repository in a convenient location before we start using
     * it.
     */
    public static final class RepositorySetDescriptor {
        public final URL m_location;
        public final String m_customer;
        public final String m_name;
        public final boolean m_writeAccess;
        public final Class<? extends ObjectRepository<?>>[] m_objectRepositories;

        public RepositorySetDescriptor(URL location, String customer, String name, boolean writeAccess, Class<? extends ObjectRepository<?>>... objectRepositories) {
            m_location = location;
            m_customer = customer;
            m_name = name;
            m_writeAccess = writeAccess;
            m_objectRepositories = objectRepositories;
        }

        @Override
        public String toString() {
            return "Repository location " + m_location.toString() + ", customer " + m_customer + ", name " + m_name;
        }
    }

    static abstract class AbstractRepositoryContext<T extends BaseRepositoryContext<?>> implements BaseRepositoryContext<T>
    {
        private URL m_location;
        private String m_name;
        private String m_customer;
        private boolean m_writeable;
        private final Class<? extends ObjectRepository<?>>[] m_repositories;

        public AbstractRepositoryContext(Class<? extends ObjectRepository<?>>... repositories) {
            if (repositories == null || repositories.length == 0) {
                throw new IllegalArgumentException("Need at least one object repository!");
            }
            m_repositories = repositories;
        }

        public T setCustomer(String customer) {
            if (customer == null) {
                throw new IllegalArgumentException("Customer cannot be null!");
            }
            m_customer = customer;
            return getThis();
        }

        public T setLocation(URL location) {
            if (location == null) {
                throw new IllegalArgumentException("Location cannot be null!");
            }
            m_location = location;
            return getThis();
        }

        public T setName(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Name cannot be null!");
            }
            m_name = name;
            return getThis();
        }

        public T setWriteable() {
            m_writeable = true;
            return getThis();
        }

        /**
         * @return a new repository set descriptor, never <code>null</code>.
         */
        final RepositorySetDescriptor createDescriptor() {
            return new RepositorySetDescriptor(m_location, m_customer, m_name, m_writeable, m_repositories);
        }

        abstract T getThis();
    }

    static final class ShopRepositoryContextImpl extends AbstractRepositoryContext<ShopRepositoryContext> implements ShopRepositoryContext {

        public ShopRepositoryContextImpl() {
            super(ArtifactRepository.class, FeatureRepository.class, Artifact2FeatureAssociationRepository.class, DistributionRepository.class, Feature2DistributionAssociationRepository.class);
        }

        @Override
        ShopRepositoryContext getThis() {
            return this;
        }
    }

    static final class TargetRepositoryContextImpl extends AbstractRepositoryContext<TargetRepositoryContext> implements TargetRepositoryContext {

        public TargetRepositoryContextImpl() {
            super(TargetRepository.class, Distribution2TargetAssociationRepository.class);
        }

        @Override
        TargetRepositoryContext getThis() {
            return this;
        }
    }

    static final class DeploymentRepositoryContextImpl extends AbstractRepositoryContext<DeploymentRepositoryContext> implements DeploymentRepositoryContext {

        public DeploymentRepositoryContextImpl() {
            super(DeploymentVersionRepository.class);
        }

        @Override
        DeploymentRepositoryContext getThis() {
            return this;
        }
    }
}
