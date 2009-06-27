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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.apache.felix.dependencymanager.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the RepositoryAdmin bundle. Creates and registers the necessary repositories,
 * plus the repository admin.
 */
public class Activator extends DependencyActivatorBase {
    private DependencyManager m_manager;
    List<Service[]> m_services;

    private RepositoryAdminImpl m_repositoryAdminImpl;
    private ChangeNotifierManager m_changeNotifierManager;

    private ArtifactRepositoryImpl m_artifactRepositoryImpl;
    private GroupRepositoryImpl m_groupRepositoryImpl;
    private Artifact2GroupAssociationRepositoryImpl m_artifact2GroupAssociationRepositoryImpl;
    private LicenseRepositoryImpl m_licenseRepositoryImpl;
    private Group2LicenseAssociationRepositoryImpl m_group2LicenseAssociationRepositoryImpl;
    private GatewayRepositoryImpl m_gatewayRepositoryImpl;
    private License2GatewayAssociationRepositoryImpl m_license2GatewayAssociationRepositoryImpl;
    private DeploymentVersionRepositoryImpl m_deploymentVersionRepositoryImpl;

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;

        m_changeNotifierManager = new ChangeNotifierManager();
        manager.add(createService()
            .setImplementation(m_changeNotifierManager)
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true)));

        m_repositoryAdminImpl = new RepositoryAdminImpl(this, m_changeNotifierManager.getConfiguredNotifier(RepositoryAdmin.PRIVATE_TOPIC_ROOT, RepositoryAdmin.PUBLIC_TOPIC_ROOT, RepositoryAdmin.TOPIC_ENTITY_ROOT));
        manager.add(createService()
            .setInterface(RepositoryAdmin.class.getName(), null)
            .setImplementation(m_repositoryAdminImpl)
            .add(createServiceDependency().setService(PreferencesService.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    private <T extends RepositoryObject> Service[] registerRepository(Class<? extends ObjectRepository<T>> iface, ObjectRepositoryImpl<?, T> imp, String[] topics) {
        Service repositoryService = createService()
            .setInterface(iface.getName(), null)
            .setImplementation(imp)
            .add(createServiceDependency().setService(LogService.class).setRequired(false));
        Dictionary<String, String[]> topic = new Hashtable<String, String[]>();
        topic.put(EventConstants.EVENT_TOPIC, topics);
        Service handlerService = createService()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(imp);

        m_manager.add(repositoryService);
        m_manager.add(handlerService);
        return new Service[] {repositoryService, handlerService};
    }

    @SuppressWarnings("unchecked")
    synchronized Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> publishRepositories() {
        // create the repository objects, if this is the first time this method is called.
        if (m_artifactRepositoryImpl == null) {
            m_artifactRepositoryImpl = new ArtifactRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, ArtifactObject.TOPIC_ENTITY_ROOT));
            m_groupRepositoryImpl = new GroupRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, GroupObject.TOPIC_ENTITY_ROOT));
            m_artifact2GroupAssociationRepositoryImpl = new Artifact2GroupAssociationRepositoryImpl(m_artifactRepositoryImpl, m_groupRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, Artifact2GroupAssociation.TOPIC_ENTITY_ROOT));
            m_licenseRepositoryImpl = new LicenseRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, LicenseObject.TOPIC_ENTITY_ROOT));
            m_group2LicenseAssociationRepositoryImpl = new Group2LicenseAssociationRepositoryImpl(m_groupRepositoryImpl, m_licenseRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, Group2LicenseAssociation.TOPIC_ENTITY_ROOT));
            m_gatewayRepositoryImpl = new GatewayRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, GatewayObject.TOPIC_ENTITY_ROOT));
            m_license2GatewayAssociationRepositoryImpl = new License2GatewayAssociationRepositoryImpl(m_licenseRepositoryImpl, m_gatewayRepositoryImpl, m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, License2GatewayAssociation.TOPIC_ENTITY_ROOT));
            m_deploymentVersionRepositoryImpl = new DeploymentVersionRepositoryImpl(m_changeNotifierManager.getConfiguredNotifier(RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, DeploymentVersionObject.TOPIC_ENTITY_ROOT));
        }
        // first, register the artifact repository manually; it needs some special care.
        Service artifactRepoService = createService()
            .setInterface(ArtifactRepository.class.getName(), null)
            .setImplementation(m_artifactRepositoryImpl)
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            .add(createServiceDependency().setService(ArtifactHelper.class).setRequired(false).setAutoConfig(false).setCallbacks(this, "addArtifactHelper", "removeArtifactHelper"));
        Dictionary<String, String[]> topic = new Hashtable<String, String[]>();
        topic.put(EventConstants.EVENT_TOPIC, new String[] {});
        Service artifactHandlerService = createService()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(m_artifactRepositoryImpl);
        m_manager.add(artifactRepoService);
        m_manager.add(artifactHandlerService);

        m_services = new ArrayList<Service[]>();
        m_services.add(new Service[] {artifactRepoService, artifactHandlerService});

        // register all repositories are services. Keep the service objects around, we need them to pull the services later.
        m_services.add(registerRepository(Artifact2GroupAssociationRepository.class, m_artifact2GroupAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(ArtifactObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(GroupObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(GroupRepository.class, m_groupRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(Group2LicenseAssociationRepository.class, m_group2LicenseAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(GroupObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(LicenseObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(LicenseRepository.class, m_licenseRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(License2GatewayAssociationRepository.class, m_license2GatewayAssociationRepositoryImpl, new String[] {createPrivateObjectTopic(LicenseObject.TOPIC_ENTITY_ROOT), createPrivateObjectTopic(GatewayObject.TOPIC_ENTITY_ROOT)}));
        m_services.add(registerRepository(GatewayRepository.class, m_gatewayRepositoryImpl, new String[] {}));
        m_services.add(registerRepository(DeploymentVersionRepository.class, m_deploymentVersionRepositoryImpl, new String[] {}));

        // prepare the results.
        Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> result = new HashMap<Class<? extends ObjectRepository>, ObjectRepositoryImpl>();

        result.put(ArtifactRepository.class, m_artifactRepositoryImpl);
        result.put(Artifact2GroupAssociationRepository.class, m_artifact2GroupAssociationRepositoryImpl);
        result.put(GroupRepository.class, m_groupRepositoryImpl);
        result.put(Group2LicenseAssociationRepository.class, m_group2LicenseAssociationRepositoryImpl);
        result.put(LicenseRepository.class, m_licenseRepositoryImpl);
        result.put(License2GatewayAssociationRepository.class, m_license2GatewayAssociationRepositoryImpl);
        result.put(GatewayRepository.class, m_gatewayRepositoryImpl);
        result.put(DeploymentVersionRepository.class, m_deploymentVersionRepositoryImpl);

        return result;
    }

    /**
     * Helper method for use in publishRepositories
     */
    private static String createPrivateObjectTopic(String entityRoot) {
        return RepositoryObject.PRIVATE_TOPIC_ROOT + entityRoot + RepositoryObject.TOPIC_ALL_SUFFIX;
    }

    /**
     * Pulls all repository services; is used to make sure the repositories go away before the RepositoryAdmin does.
     */
    synchronized void pullRepositories() {
        for (Service[] services : m_services) {
            for (Service service : services) {
                m_manager.remove(service);
            }
        }
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        if (m_repositoryAdminImpl.loggedIn()) {
            try {
                m_repositoryAdminImpl.logout(true);
            }
            catch (IOException ioe) {
                // Not much to do about this. We could log it?
            }
        }
        m_repositoryAdminImpl = null;
    }

    public void addArtifactHelper(ServiceReference ref, ArtifactHelper helper) {
        String mimetype = (String) ref.getProperty(ArtifactHelper.KEY_MIMETYPE);
        m_artifactRepositoryImpl.addHelper(mimetype, helper);
    }

    public synchronized void removeArtifactHelper(ServiceReference ref, ArtifactHelper helper) {
        String mimetype = (String) ref.getProperty(ArtifactHelper.KEY_MIMETYPE);
        m_artifactRepositoryImpl.removeHelper(mimetype, helper);
    }
}
