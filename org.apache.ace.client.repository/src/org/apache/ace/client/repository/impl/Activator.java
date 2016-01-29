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
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.client.repository.stateful.impl.StatefulTargetRepositoryImpl;
import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the RepositoryAdmin bundle. Creates the repository admin, which internally creates all required
 * repositories.
 */
public class Activator extends DependencyActivatorBase implements SessionFactory, ManagedService {
    /**
     * Small container that keeps the session-related services for us.
     */
    private static final class SessionData {
        private final List<Component> m_services = new ArrayList<>();

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

        final boolean isEmpty() {
            synchronized (m_services) {
                return m_services.isEmpty();
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
    }

    private static final String PID = "org.apache.ace.client.repository";

    private final Map<String, SessionData> m_sessions = new HashMap<>();
    private final RepositoryConfigurationImpl m_repoConfiguration = new RepositoryConfigurationImpl();

    private volatile DependencyManager m_dependencyManager;

    public void createSession(String sessionID, Map sessionConfiguration) {
        SessionData sessionData = null;
        synchronized (m_sessions) {
            if (!m_sessions.containsKey(sessionID)) {
                sessionData = new SessionData();
                m_sessions.put(sessionID, sessionData);
            }
        }

        // Allow session to be created outside the lock; to avoid potential deadlocks...
        if (sessionData != null) {
            createSessionServices(sessionData, sessionID, getConfiguration(sessionConfiguration));
        }
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

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

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_dependencyManager = manager;

        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID);
        props.put(CommandProcessor.COMMAND_SCOPE, "clientrepo");
        props.put(CommandProcessor.COMMAND_FUNCTION, new String[] { "sessions" });

        manager.add(createComponent()
            .setInterface(new String[] { SessionFactory.class.getName(), ManagedService.class.getName() }, props)
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

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        m_repoConfiguration.update(properties);
    }

    /**
     * Creates all necessary session-related service for the given session.
     * 
     * @param sd
     *            the session data to keep the session-related services;
     * @param sessionID
     *            the session ID to use.
     */
    private void createSessionServices(SessionData sd, String sessionID, RepositoryConfiguration repoConfig) {
        RepositoryAdminImpl rai = new RepositoryAdminImpl(sessionID, repoConfig);
        Component repositoryAdminComponent = createComponent()
            .setInterface(RepositoryAdmin.class.getName(), rai.getSessionProps())
            .setImplementation(rai)
            .setComposition("getInstances")
            .add(createServiceDependency().setService(PreferencesService.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        String sessionFilter = "(" + SessionFactory.SERVICE_SID + "=" + sessionID + ")";
        String auditLogFilter = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))";

        Dictionary<String, Object> topic = new Hashtable<>();
        topic.put(SessionFactory.SERVICE_SID, sessionID);
        topic.put(EventConstants.EVENT_FILTER, sessionFilter);
        topic.put(EventConstants.EVENT_TOPIC, new String[] {
            ArtifactObject.PRIVATE_TOPIC_ALL,
            Artifact2FeatureAssociation.PRIVATE_TOPIC_ALL,
            FeatureObject.PRIVATE_TOPIC_ALL,
            Feature2DistributionAssociation.PRIVATE_TOPIC_ALL,
            DistributionObject.PRIVATE_TOPIC_ALL,
            Distribution2TargetAssociation.PRIVATE_TOPIC_ALL,
            TargetObject.PRIVATE_TOPIC_ALL,
            DeploymentVersionObject.PRIVATE_TOPIC_ALL,
            RepositoryAdmin.PRIVATE_TOPIC_HOLDUNTILREFRESH,
            RepositoryAdmin.PRIVATE_TOPIC_REFRESH,
            RepositoryAdmin.PRIVATE_TOPIC_LOGIN
        });

        StatefulTargetRepositoryImpl statefulTargetRepositoryImpl = new StatefulTargetRepositoryImpl(sessionID, repoConfig);
        Component statefulTargetRepositoryComponent = createComponent()
            .setInterface(new String[] { StatefulTargetRepository.class.getName(), EventHandler.class.getName() }, topic)
            .setImplementation(statefulTargetRepositoryImpl)
            .add(createServiceDependency().setService(ArtifactRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(TargetRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class, sessionFilter).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, auditLogFilter).setRequired(false))
            .add(createServiceDependency().setService(BundleHelper.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        rai.addPreCommitMember(statefulTargetRepositoryImpl);

        // Publish our components to our session data for later use...
        sd.addComponents(m_dependencyManager, repositoryAdminComponent, statefulTargetRepositoryComponent);
    }

    /**
     * Removes the session-related services from the session.
     * 
     * @param sd
     *            the session data that keeps the session-related services;
     * @param sessionID
     *            the session ID to use.
     */
    private void destroySessionServices(SessionData sd, String sessionID) {
        sd.removeAllComponents(m_dependencyManager);
    }

    /**
     * Creates a copy of the repository configuration that is supposed to remain stable for the duration of a session,
     * if there are settings overridden. If no settings are to be overridden, the current (mutable!) repository
     * configuration is used.
     * 
     * @param sessionConfiguration
     *            the session configuration overriding the current repository configuration values, can be
     *            <code>null</code> to keep the current configuration as-is.
     * @return a new {@link RepositoryConfiguration} instance, never <code>null</code>.
     */
    private RepositoryConfiguration getConfiguration(Map<String, Object> sessionConfiguration) {
        if (sessionConfiguration != null) {
            return new RepositoryConfigurationImpl(m_repoConfiguration, sessionConfiguration);
        }
        return m_repoConfiguration;
    }
}
