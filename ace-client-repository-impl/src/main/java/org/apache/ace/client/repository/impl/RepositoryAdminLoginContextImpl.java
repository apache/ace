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
import java.util.List;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.osgi.service.useradmin.User;

class RepositoryAdminLoginContextImpl implements RepositoryAdminLoginContext {
    private final User m_user;
    private final List<RepositorySetDescriptor> m_descriptors = new ArrayList<RepositorySetDescriptor>();
    private URL m_obrBase;
    private final String m_sessionid;

    RepositoryAdminLoginContextImpl(User user, String sessionid) {
        m_user = user;
        m_sessionid = sessionid;
    }

    @SuppressWarnings("unchecked")
    public RepositoryAdminLoginContext addRepositories(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess, Class<? extends ObjectRepository>... objectRepositories) {
        if ((repositoryLocation == null) || (repositoryCustomer == null) || (repositoryName == null)) {
            throw new IllegalArgumentException("No parameter should be null.");
        }
        if ((objectRepositories == null) || (objectRepositories.length == 0)) {
            throw new IllegalArgumentException("objectRepositories should not be null or empty.");
        }
        m_descriptors.add(new RepositorySetDescriptor(repositoryLocation, repositoryCustomer, repositoryName, writeAccess, objectRepositories));
        return this;
    }

    @SuppressWarnings("unchecked")
    public RepositoryAdminLoginContext addShopRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess) {
        return addRepositories(repositoryLocation, repositoryCustomer, repositoryName, writeAccess,
            ArtifactRepository.class,
            GroupRepository.class,
            Artifact2GroupAssociationRepository.class,
            LicenseRepository.class,
            Group2LicenseAssociationRepository.class);
    }

    @SuppressWarnings("unchecked")
    public RepositoryAdminLoginContext addGatewayRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess) {
        return addRepositories(repositoryLocation, repositoryCustomer, repositoryName, writeAccess,
            GatewayRepository.class,
            License2GatewayAssociationRepository.class);
    }

    @SuppressWarnings("unchecked")
    public RepositoryAdminLoginContext addDeploymentRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess) {
        return addRepositories(repositoryLocation, repositoryCustomer, repositoryName, writeAccess,
            DeploymentVersionRepository.class);
    }

    public RepositoryAdminLoginContext setObrBase(URL base) {
        m_obrBase = base;
        return this;
    }

    URL getObrBase() {
        return m_obrBase;
    }

    User getUser() {
        return m_user;
    }

    public List<RepositorySetDescriptor> getDescriptors() {
        return m_descriptors;
    }

    /**
     * Helper class to store all relevant information about a repository in a convenient location before
     * we start using it.
     */
    static class RepositorySetDescriptor {
        public final URL m_location;
        public final String m_customer;
        public final String m_name;
        public final boolean m_writeAccess;
        @SuppressWarnings("unchecked")
        public final Class<? extends ObjectRepository>[] m_objectRepositories;

        @SuppressWarnings("unchecked")
        RepositorySetDescriptor(URL location, String customer, String name, boolean writeAccess, Class<? extends ObjectRepository>... objectRepositories) {
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
}
