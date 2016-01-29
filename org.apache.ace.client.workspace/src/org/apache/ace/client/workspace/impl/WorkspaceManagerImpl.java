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
package org.apache.ace.client.workspace.impl;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.workspace.Workspace;
import org.apache.ace.client.workspace.WorkspaceManager;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

@SuppressWarnings("rawtypes")
public class WorkspaceManagerImpl implements ManagedService, WorkspaceManager {
    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    /** URL of the repository to talk to. */
    private static final String KEY_REPOSITORY_URL = "repository.url";
    /** Name of the customer. */
    private static final String KEY_CUSTOMER_NAME = "customer.name";
    /** Name of the store repository. */
    private static final String KEY_STORE_REPOSITORY_NAME = "store.repository.name";
    /** Name of the distribution repository. */
    private static final String KEY_DISTRIBUTION_REPOSITORY_NAME = "distribution.repository.name";
    /** Name of the deployment repository. */
    private static final String KEY_DEPLOYMENT_REPOSITORY_NAME = "deployment.repository.name";
    /** Name of the user to log in as, in case no actual authentication is used. */
    private static final String KEY_USER_NAME = "user.name";

    private static long m_sessionID = 1;
    private volatile LogService m_logger;
    private volatile DependencyManager m_dm;
    private volatile SessionFactory m_sessionFactory;

    private volatile AuthenticationService m_authenticationService;
    private volatile UserAdmin m_userAdmin;

    private final Map<String, Workspace> m_workspaces;
    private final Map<String, Component> m_workspaceComponents;

    private boolean m_useAuthentication;
    private String m_repositoryURL;
    private String m_customerName;
    private String m_storeRepositoryName;
    private String m_targetRepositoryName;
    private String m_deploymentRepositoryName;
    private String m_serverUser;

    public WorkspaceManagerImpl() {
        m_workspaces = new HashMap<>();
        m_workspaceComponents = new HashMap<>();
    }

    public void init(Component component) {
        addDependency(component, AuthenticationService.class, m_useAuthentication);
        addDependency(component, UserAdmin.class, true);
    }

    private void addDependency(Component component, Class<?> service, boolean isRequired) {
        component.add(component.getDependencyManager().createServiceDependency().setService(service)
                .setRequired(isRequired));
    }

    public void destroy() {
        Set<String> keySet = m_workspaces.keySet();
        if (!keySet.isEmpty()) {
            String[] keys = keySet.toArray(new String[keySet.size()]);
            for (String key : keys) {
                try {
                    removeWorkspace(key);
                }
                catch (IOException e) {
                    m_logger.log(LogService.LOG_WARNING, "Could not properly remove workspace.", e);
                }
            }
        }
    }

    @Override
    public void updated(Dictionary properties) throws ConfigurationException {
        // First check whether all mandatory configuration keys are available...
        String useAuth = getProperty(properties, KEY_USE_AUTHENTICATION);
        if (useAuth == null || !("true".equalsIgnoreCase(useAuth) || "false".equalsIgnoreCase(useAuth))) {
            throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
        }

        // Note that configuration changes are only applied to new work areas, started after the
        // configuration was changed. No attempt is done to "fix" existing work areas, although we
        // might consider flushing/invalidating them.
        synchronized (m_workspaces) {
            m_useAuthentication = Boolean.valueOf(useAuth);
            m_repositoryURL = getProperty(properties, KEY_REPOSITORY_URL, "http://localhost:8080/repository");
            m_customerName = getProperty(properties, KEY_CUSTOMER_NAME, "apache");
            m_storeRepositoryName = getProperty(properties, KEY_STORE_REPOSITORY_NAME, "shop");
            m_targetRepositoryName = getProperty(properties, KEY_DISTRIBUTION_REPOSITORY_NAME, "target");
            m_deploymentRepositoryName = getProperty(properties, KEY_DEPLOYMENT_REPOSITORY_NAME, "deployment");
            m_serverUser = getProperty(properties, KEY_USER_NAME, "d");
        }
    }

    @Override
    public Workspace createWorkspace(Map sessionConfiguration, Object... authenticationContext) throws IOException {
        // TODO get data from post body (if no data, assume latest??) -> for now
        // always assume latest
        final String sessionID;
        final Workspace workspace;
        final Component component;

        synchronized (m_workspaces) {
            sessionID = "rest-" + m_sessionID++;
            workspace = new WorkspaceImpl(sessionID, m_repositoryURL, m_customerName, m_storeRepositoryName,
                    m_targetRepositoryName, m_deploymentRepositoryName);
            m_workspaces.put(sessionID, workspace);

            component = m_dm.createComponent().setImplementation(workspace);
            m_workspaceComponents.put(sessionID, component);
        }
        // any parameters supplied in this call are passed on to the session
        // factory, so you can use these to configure your session
        m_sessionFactory.createSession(sessionID, sessionConfiguration);
        m_dm.add(component);

        User user;
        if (m_useAuthentication) {
            // Use the authentication service to authenticate the given
            // request...
            user = m_authenticationService.authenticate(authenticationContext);
        }
        else {
            // Use the "hardcoded" user to login with...
            user = m_userAdmin.getUser("username", m_serverUser);
        }

        if (user == null || !workspace.login(user)) {
            return null;
        }
        else {
            return workspace;
        }
    }

    @Override
    public Workspace getWorkspace(String id) {
        Workspace workspace;
        synchronized (m_workspaces) {
            workspace = m_workspaces.get(id);
        }
        return workspace;
    }

    @Override
    public void removeWorkspace(final String id) throws IOException {
        final Workspace workspace;
        final Component component;

        synchronized (m_workspaces) {
            workspace = m_workspaces.remove(id);
            component = m_workspaceComponents.remove(id);
        }

        if ((workspace != null) && (component != null)) {
            try {
                workspace.logout();
            }
            finally {
                m_dm.remove(component);
                m_sessionFactory.destroySession(id);
            }
        }
    }

    @Override
    public Workspace cw() throws IOException {
        return cw(m_customerName, m_customerName, m_customerName, null);
    }

    @Override
    public Workspace cw(Map sessionConfiguration) throws IOException {
        return cw(m_customerName, m_customerName, m_customerName, sessionConfiguration);
    }

    @Override
    public Workspace cw(String storeCustomerName, String targetCustomerName, String deploymentCustomerName)
            throws IOException {
        return cw(storeCustomerName, targetCustomerName, deploymentCustomerName, null);
    }

    @Override
    public Workspace cw(String storeCustomerName, String targetCustomerName, String deploymentCustomerName,
            Map sessionConfiguration) throws IOException {
        final String sessionID;
        final Workspace workspace;
        final Component component;

        synchronized (m_workspaces) {
            sessionID = "shell-" + m_sessionID++;
            workspace = new WorkspaceImpl(sessionID, m_repositoryURL, storeCustomerName, m_storeRepositoryName,
                    targetCustomerName, m_targetRepositoryName, deploymentCustomerName, m_deploymentRepositoryName);
            m_workspaces.put(sessionID, workspace);

            component = m_dm.createComponent().setImplementation(workspace);
            m_workspaceComponents.put(sessionID, component);
        }
        m_sessionFactory.createSession(sessionID, sessionConfiguration);
        m_dm.add(component);

        // Use the "hardcoded" user to login with...
        User user = m_userAdmin.getUser("username", m_serverUser);
        if (user == null || !workspace.login(user)) {
            return null;
        }
        else {
            return workspace;
        }
    }

    @Override
    public Workspace gw(String id) {
        return getWorkspace(id);
    }

    @Override
    public void rw(String id) throws IOException {
        removeWorkspace(id);
    }

    @Override
    public void rw(Workspace w) throws IOException {
        removeWorkspace(w.getSessionID());
    }

    /**
     * Helper method to safely obtain a property value from the given dictionary.
     * 
     * @param properties
     *            the dictionary to retrieve the value from, can be <code>null</code>;
     * @param key
     *            the name of the property to retrieve, cannot be <code>null</code>;
     * @param defaultValue
     *            the default value to return in case the property does not exist, or the given dictionary was
     *            <code>null</code>.
     * @return a property value, can be <code>null</code>.
     */
    String getProperty(Dictionary properties, String key, String defaultValue) {
        String value = getProperty(properties, key);
        return (value == null) ? defaultValue : value;
    }

    /**
     * Helper method to safely obtain a property value from the given dictionary.
     * 
     * @param properties
     *            the dictionary to retrieve the value from, can be <code>null</code>;
     * @param key
     *            the name of the property to retrieve, cannot be <code>null</code>.
     * @return a property value, can be <code>null</code>.
     */
    String getProperty(Dictionary properties, String key) {
        if (properties != null) {
            Object value = properties.get(key);
            if (value != null && value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }
}
