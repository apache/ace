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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.client.repository.stateful.impl.StatefulGatewayRepositoryImpl;
import org.apache.ace.server.log.store.LogStore;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.service.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the RepositoryAdmin bundle. Creates the repository admin, which internally
 * creates all required repositories.
 */
public class Activator extends DependencyActivatorBase implements SessionFactory {
    private DependencyManager m_dependencyManager;

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        m_dependencyManager = manager;
        manager.add(createComponent()
            .setInterface(SessionFactory.class.getName(), null)
            .setImplementation(this)
        );
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    private Map<String, SessionData> m_sessions = new HashMap<String, SessionData>();
    private static class SessionData {
        public static SessionData EMPTY_SESSION = new SessionData();

        private Service m_service;
        private Service m_service2;
    }

    public void createSession(String sessionID) {
        boolean create = false;
        synchronized (m_sessions) {
            if (!m_sessions.containsKey(sessionID)) {
                m_sessions.put(sessionID, SessionData.EMPTY_SESSION);
                create = true;
            }
        }
        if (create) {
            SessionData sd = createSessionServices(sessionID);
            m_sessions.put(sessionID, sd);
        }
    }

    public void destroySession(String sessionID) {
        boolean destroy = false;
        SessionData sd = SessionData.EMPTY_SESSION;
        synchronized (m_sessions) {
            destroy = m_sessions.containsKey(sessionID);
            sd = m_sessions.remove(sessionID);
        }
        if (destroy && !sd.equals(SessionData.EMPTY_SESSION)) {
            destroySessionServices(sessionID, sd);
        }
    }

    @SuppressWarnings("unchecked")
    private SessionData createSessionServices(String sessionID) {
        SessionData sd = new SessionData();
        RepositoryAdminImpl rai = new RepositoryAdminImpl(sessionID);
        sd.m_service = createComponent()
            .setInterface(RepositoryAdmin.class.getName(), rai.getSessionProps())
            .setImplementation(rai)
            .setComposition("getInstances")
            .add(createServiceDependency().setService(PreferencesService.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));
        m_dependencyManager.add(sd.m_service);

        Dictionary topic = new Hashtable();
        topic.put(EventConstants.EVENT_TOPIC, new String[] {
            ArtifactObject.TOPIC_ALL,
            Artifact2GroupAssociation.TOPIC_ALL,
            GroupObject.TOPIC_ALL,
            Group2LicenseAssociation.TOPIC_ALL,
            LicenseObject.TOPIC_ALL,
            License2GatewayAssociation.TOPIC_ALL,
            GatewayObject.TOPIC_ALL,
            DeploymentVersionObject.TOPIC_ALL,
            RepositoryAdmin.TOPIC_REFRESH, RepositoryAdmin.TOPIC_LOGIN});
        String filter = "(" + SessionFactory.SERVICE_SID + "=" + sessionID + ")";
        topic.put(EventConstants.EVENT_FILTER, filter);
        topic.put(SessionFactory.SERVICE_SID, sessionID);
        StatefulGatewayRepositoryImpl statefulGatewayRepositoryImpl = new StatefulGatewayRepositoryImpl();
        sd.m_service2 = createComponent()
            .setInterface(new String[] { StatefulGatewayRepository.class.getName(), EventHandler.class.getName() }, topic)
            .setImplementation(statefulGatewayRepositoryImpl)
            .add(createServiceDependency().setService(ArtifactRepository.class, filter).setRequired(true))
            .add(createServiceDependency().setService(GatewayRepository.class, filter).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class, filter).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(false))
            .add(createServiceDependency().setService(BundleHelper.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));
        m_dependencyManager.add(sd.m_service2);
        return sd;
    }

    private void destroySessionServices(String sessionID, SessionData sd) {
        m_dependencyManager.remove(sd.m_service2);
        m_dependencyManager.remove(sd.m_service);
    }
}
