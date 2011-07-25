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
package org.apache.ace.client.rest;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

public class Workspace {
    private static final String ARTIFACT = "artifact";
    private static final String ARTIFACT2FEATURE = "artifact2feature";
    private static final String FEATURE = "feature";
    private static final String FEATURE2DISTRIBUTION = "feature2distribution";
    private static final String DISTRIBUTION = "distribution";
    private static final String DISTRIBUTION2TARGET = "distribution2target";
    private static final String TARGET = "target";
    private final String m_sessionID;
    private final String m_repositoryURL;
    private final String m_obrURL;
    private final String m_customerName;
    private final String m_storeRepositoryName;
    private final String m_licenseRepositoryName;
    private final String m_deploymentRepositoryName;
    private final String m_serverUser;
    private volatile DependencyManager m_manager;
    private volatile RepositoryAdmin m_repositoryAdmin;
    private volatile ArtifactRepository m_artifactRepository;
    private volatile GroupRepository m_featureRepository;
    private volatile LicenseRepository m_distributionRepository;
    private volatile StatefulGatewayRepository m_statefulTargetRepository;
    private volatile GatewayRepository m_targetRepository;
    private volatile Artifact2GroupAssociationRepository m_artifact2FeatureAssociationRepository;
    private volatile Group2LicenseAssociationRepository m_feature2DistributionAssociationRepository;
    private volatile License2GatewayAssociationRepository m_distribution2TargetAssociationRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile LogService m_log;

    public Workspace(String sessionID, String repositoryURL, String obrURL, String customerName, String storeRepositoryName, String licenseRepositoryName, String deploymentRepositoryName, String serverUser) {
        m_sessionID = sessionID;
        m_repositoryURL = repositoryURL;
        m_obrURL = obrURL;
        m_customerName = customerName;
        m_storeRepositoryName = storeRepositoryName;
        m_licenseRepositoryName = licenseRepositoryName;
        m_deploymentRepositoryName = deploymentRepositoryName;
        m_serverUser = serverUser;
    }
    
    private void addSessionDependency(Component component, Class service, boolean isRequired) {
        component.add(m_manager.createServiceDependency()
            .setService(service, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(isRequired)
            .setInstanceBound(true)
            );
    }
    
    private void addDependency(Component component, Class service, boolean isRequired) {
        component.add(m_manager.createServiceDependency()
            .setService(service)
            .setRequired(isRequired)
            .setInstanceBound(true)
            );
    }
    
    public void init(Component component) {
        addSessionDependency(component, RepositoryAdmin.class, true);
        addSessionDependency(component, ArtifactRepository.class, true);
        addSessionDependency(component, GroupRepository.class, true);
        addSessionDependency(component, LicenseRepository.class, true);
        addSessionDependency(component, GatewayRepository.class, true);
        addSessionDependency(component, StatefulGatewayRepository.class, true);
        addSessionDependency(component, Artifact2GroupAssociationRepository.class, true);
        addSessionDependency(component, Group2LicenseAssociationRepository.class, true);
        addSessionDependency(component, License2GatewayAssociationRepository.class, true);
        addDependency(component, UserAdmin.class, true);
        addDependency(component, LogService.class, false);
    }
    
    public void start() {
        try {
            User user = m_userAdmin.getUser("username", m_serverUser);
            m_repositoryAdmin.login(m_repositoryAdmin.createLoginContext(user)
                .setObrBase(new URL(m_obrURL))
                .addShopRepository(new URL(m_repositoryURL), m_customerName, m_storeRepositoryName, true)
                .addGatewayRepository(new URL(m_repositoryURL), m_customerName, m_licenseRepositoryName, true)
                .addDeploymentRepository(new URL(m_repositoryURL), m_customerName, m_deploymentRepositoryName, true)
                );
            m_repositoryAdmin.checkout();
//            m_repositoryAdmin.revert();
        }
        catch (IOException e) {
            e.printStackTrace();
            m_log.log(LogService.LOG_ERROR, "Could not login and checkout. Workspace will probably not work correctly.", e);
        }
    }
    
    public void destroy() {
    }

    public void commit() throws IOException {
        m_repositoryAdmin.commit();
    }

    public RepositoryObject getRepositoryObject(String entityType, String entityId) {
        RepositoryObject result = null;
        try {
            List list = null;
            Filter filter = FrameworkUtil.createFilter(entityId);
            list = getObjectRepository(entityType).get(filter);
            if (list != null && list.size() == 1) {
                return (RepositoryObject) list.get(0);
            }
        }
        catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<RepositoryObject> getRepositoryObjects(String entityType) {
        List list = getObjectRepository(entityType).get();
        if (list != null) {
            return list;
        }
        else {
            return Collections.EMPTY_LIST;
        }
    }

    public RepositoryObject addRepositoryObject(String entityType, Map<String, String> attributes, Map<String, String> tags) throws IllegalArgumentException{
        return getObjectRepository(entityType).create(attributes, tags);
    }
    
    public void deleteRepositoryObject(String entityType, String entityId) {
        RepositoryObject result = null;
        try {
            List list = null;
            Filter filter = FrameworkUtil.createFilter(entityId);
            ObjectRepository objectRepository = getObjectRepository(entityType);
            list = objectRepository.get(filter);
            if (list != null && list.size() == 1) {
                objectRepository.remove((RepositoryObject) list.get(0));
            }
        }
        catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    private ObjectRepository getObjectRepository(String entityType) {
        if (ARTIFACT.equals(entityType)) {
            return m_artifactRepository;
        }
        if (ARTIFACT2FEATURE.equals(entityType)) {
            return m_artifact2FeatureAssociationRepository;
        }
        if (FEATURE.equals(entityType)) {
            return m_featureRepository;
        }
        if (FEATURE2DISTRIBUTION.equals(entityType)) {
            return m_feature2DistributionAssociationRepository;
        }
        if (DISTRIBUTION.equals(entityType)) {
            return m_distributionRepository;
        }
        if (DISTRIBUTION2TARGET.equals(entityType)) {
            return m_distribution2TargetAssociationRepository;
        }
        if (TARGET.equals(entityType)) {
            return m_targetRepository;
        }
        throw new IllegalArgumentException("Unknown entity type: " + entityType);
    }
}
