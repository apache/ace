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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.client.repository.stateful.impl.StatefulTargetRepositoryImpl;
import org.apache.ace.server.log.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
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
    
    private final Map<String, SessionData> m_sessions = new HashMap<String, SessionData>();

    private volatile DependencyManager m_dependencyManager;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        m_dependencyManager = manager;
        
        Properties props = new Properties();
        props.put(CommandProcessor.COMMAND_SCOPE, "clientrepo");
        props.put(CommandProcessor.COMMAND_FUNCTION, new String[] { "sessions" });
        
        manager.add(createComponent()
            .setInterface(SessionFactory.class.getName(), props)
            .setImplementation(this)
        );
    }

    /** Shell command to show the active sessions. */
    public void sessions() {
        synchronized (m_sessions) {
        	if (m_sessions.isEmpty()) {
        		System.out.println("No sessions.");
        	}
        	else {
        		System.out.println("Sessions:");
        		for (Entry<String, SessionData> session : m_sessions.entrySet()) {
        			System.out.println(" * " + session.getKey());
        		}
        	}
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nop
    }

    /**
     * {@inheritDoc}
     */
    public void createSession(String sessionID) {
        SessionData sessionData = null;
        synchronized (m_sessions) {
            if (!m_sessions.containsKey(sessionID)) {
                sessionData = new SessionData();
                m_sessions.put(sessionID, sessionData);
            }
        }

        // Allow session to be created outside the lock; to avoid potential deadlocks...
        if (sessionData != null) {
            createSessionServices(sessionData, sessionID);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void destroySession(String sessionID) {
        SessionData sessionData = null;
        synchronized (m_sessions) {
            sessionData = m_sessions.remove(sessionID);
        }

        // Allow session to be destroyed outside the lock; to avoid potential deadlocks...
        if ((sessionData != null) && !sessionData.isEmpty()) {
            destroySessionServices(sessionData, sessionID);
        }
    }

    /**
     * Creates all necessary session-related service for the given session.
     * 
     * @param sd the session data to keep the session-related services;
     * @param sessionID the session ID to use.
     */
    @SuppressWarnings("unchecked")
    private void createSessionServices(SessionData sd, String sessionID) {
        RepositoryAdminImpl rai = new RepositoryAdminImpl(sessionID);
        Component comp1 = createComponent()
            .setInterface(RepositoryAdmin.class.getName(), rai.getSessionProps())
            .setImplementation(rai)
            .setComposition("getInstances")
            .add(createServiceDependency().setService(PreferencesService.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        String sessionFilter = "(" + SessionFactory.SERVICE_SID + "=" + sessionID + ")";
        String auditLogFilter = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))";

        Dictionary topic = new Hashtable();
        topic.put(SessionFactory.SERVICE_SID, sessionID);
        topic.put(EventConstants.EVENT_FILTER, sessionFilter);
        topic.put(EventConstants.EVENT_TOPIC, new String[] {
            ArtifactObject.TOPIC_ALL,
            Artifact2FeatureAssociation.TOPIC_ALL,
            FeatureObject.TOPIC_ALL,
            Feature2DistributionAssociation.TOPIC_ALL,
            DistributionObject.TOPIC_ALL,
            Distribution2TargetAssociation.TOPIC_ALL,
            TargetObject.TOPIC_ALL,
            DeploymentVersionObject.TOPIC_ALL,
            RepositoryAdmin.TOPIC_REFRESH, 
            RepositoryAdmin.TOPIC_LOGIN 
        });
        
        StatefulTargetRepositoryImpl statefulTargetRepositoryImpl = new StatefulTargetRepositoryImpl(sessionID);
        Component comp2 = createComponent()
            .setInterface(new String[] { StatefulTargetRepository.class.getName(), EventHandler.class.getName() }, topic)
            .setImplementation(statefulTargetRepositoryImpl)
            .add(createServiceDependency().setService(ArtifactRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(TargetRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, auditLogFilter).setRequired(false))
            .add(createServiceDependency().setService(BundleHelper.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        // Publish our components to our session data for later use...
        sd.addComponents(m_dependencyManager, comp1, comp2);
    }

    /**
     * Removes the session-related services from the session.
     * 
     * @param sd the session data that keeps the session-related services;
     * @param sessionID the session ID to use.
     */
    private void destroySessionServices(SessionData sd, String sessionID) {
        sd.removeAllComponents(m_dependencyManager);
    }

    /**
     * Small container that keeps the session-related services for us.
     */
    private static final class SessionData {
        private final List<Component> m_services = new ArrayList<Component>();

        final void addComponents(DependencyManager manager, Component... comps) {
            synchronized (m_services) {
                for (Component c : comps) {
                    m_services.add(c);
                }
            }

            for (Component c : comps) {
                manager.add(c);
            }
        }

        final void removeAllComponents(DependencyManager manager) {
            Component[] comps;
            synchronized (m_services) {
                comps = m_services.toArray(new Component[m_services.size()]);
                m_services.clear();
            }
            
            for (Component c : comps) {
                manager.remove(c);
            }
        }
        
        final boolean isEmpty() {
            synchronized (m_services) {
                return m_services.isEmpty();
            }
        }
    }
}
