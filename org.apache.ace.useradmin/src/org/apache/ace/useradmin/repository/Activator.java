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

import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_CUSTOMER;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_NAME;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.impl.RemoteRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.useradmin.RoleRepositoryStore;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdminListener;

public class Activator extends DependencyActivatorBase implements ManagedService {
    private static final String PID = "org.apache.ace.useradmin.repository";

    public static final String KEY_REPOSITORY_CUSTOMER = "repositoryCustomer";
    public static final String KEY_REPOSITORY_NAME = "repositoryName";
    public static final String KEY_REPOSITORY_LOCATION = "repositoryLocation";

    private final List<Component> m_components = new ArrayList<>();

    private volatile DependencyManager m_manager;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(this)
            .add(createConfigurationDependency().setPid(PID)));
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        removeOldComponents();

        if (properties == null) {
            return;
        }

        String customer = (String) properties.get(KEY_REPOSITORY_CUSTOMER);
        if ((customer == null) || "".equals(customer.trim())) {
            throw new ConfigurationException(KEY_REPOSITORY_CUSTOMER, "Repository customer has to be specified.");
        }

        String name = (String) properties.get(KEY_REPOSITORY_NAME);
        if ((name == null) || "".equals(name.trim())) {
            throw new ConfigurationException(KEY_REPOSITORY_NAME, "Repository name has to be specified.");
        }

        URL repositoryUrl = null;
        String repositoryUrlStr = (String) properties.get(KEY_REPOSITORY_LOCATION);
        if (repositoryUrlStr != null) {
            if (!"".equals(repositoryUrlStr.trim())) {
                try {
                    repositoryUrl = new URL(repositoryUrlStr);
                }
                catch (MalformedURLException exception) {
                    throw new ConfigurationException(KEY_REPOSITORY_LOCATION, "Repository location has to be a valid URL.");
                }
            }
        }

        String repoFilter = String.format("(&(customer=%s)(name=%s)(|(master=true)(remote=true)))", customer, name);

        Component repoComp = null;
        Component storeComp = m_manager.createComponent()
            .setInterface(new String[] { RoleRepositoryStore.class.getName(), UserAdminListener.class.getName() }, null)
            .setImplementation(UserAdminRepository.class)
            .add(m_manager.createServiceDependency().setService(Repository.class, repoFilter).setRequired(true))
            .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false));

        if (repositoryUrl != null) {
            // Remote version...
            Properties repoProps = new Properties();
            repoProps.put(REPOSITORY_CUSTOMER, customer);
            repoProps.put(REPOSITORY_NAME, name);
            repoProps.put("remote", "true");

            repoComp = m_manager.createComponent()
                .setInterface(Repository.class.getName(), repoProps)
                .setImplementation(new RemoteRepository(repositoryUrl, customer, name))
                .add(m_manager.createServiceDependency()
                    .setService(ConnectionFactory.class)
                    .setRequired(true));
        }

        synchronized (m_components) {
            m_components.add(storeComp);
            m_manager.add(storeComp);

            if (repoComp != null) {
                m_components.add(repoComp);
                m_manager.add(repoComp);
            }
        }
    }

    private void removeOldComponents() {
        synchronized (m_components) {
            Iterator<Component> iter = m_components.iterator();
            while (iter.hasNext()) {
                m_manager.remove(iter.next());
                iter.remove();
            }
        }
    }
}
