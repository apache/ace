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

public class Activator extends DependencyActivatorBase {

    private static final String PID = "org.apache.ace.useradmin.repository";
    public static final String KEY_REPOSITORY_CUSTOMER = "repositoryCustomer";
    public static final String KEY_REPOSITORY_NAME = "repositoryName";
    public static final String KEY_REPOSITORY_LOCATION = "repositoryLocation";
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        
        manager.add(createComponent().setImplementation(new RemoteRepositoryManager())
            .add(createConfigurationDependency().setPid(PID))
            );
    }
    
    private static class RemoteRepositoryManager implements ManagedService {
        
        private volatile DependencyManager m_manager;
        private final List<Component> m_components = new ArrayList<>();
    
        @Override
        public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
            if (properties == null) {
                Iterator<Component> iterator = m_components.iterator();
                while (iterator.hasNext()) {
                    Component component = (Component) iterator.next();
                    m_manager.remove(component);
                    iterator.remove();
                }
                return;
            }
            
            String customer = (String) properties.get(KEY_REPOSITORY_CUSTOMER);
            if ((customer == null) || "".equals(customer)) {
                throw new ConfigurationException(KEY_REPOSITORY_CUSTOMER, "Repository customer has to be specified.");
            }

            String name = (String) properties.get(KEY_REPOSITORY_NAME);
            if ((name == null) || "".equals(name)) {
                throw new ConfigurationException(KEY_REPOSITORY_NAME, "Repository name has to be specified.");
            }
            
            String repositoryUrl = (String) properties.get(KEY_REPOSITORY_LOCATION);
            if ((repositoryUrl == null) || "".equals(repositoryUrl)) {
                throw new ConfigurationException(KEY_REPOSITORY_LOCATION, "Repository location has to be specified.");
            }
            
            try {
                //CachedRepo
                RemoteRepository remoteRepository = new RemoteRepository(new URL(repositoryUrl), customer, name);
                Properties repoProps = new Properties();
                repoProps.put(REPOSITORY_CUSTOMER, customer);
                repoProps.put(REPOSITORY_NAME, name);
                
                Component repositoryComponent = m_manager.createComponent()
                    .setInterface(RemoteRepository.class.getName(), repoProps)
                    .setImplementation(remoteRepository)
                    .add(m_manager.createServiceDependency()
                        .setService(ConnectionFactory.class)
                        .setRequired(true)
                    );
    
                m_manager.add(repositoryComponent);
                m_components.add(repositoryComponent);
                
            } catch (MalformedURLException e) {
                throw new ConfigurationException(KEY_REPOSITORY_LOCATION, "Repository location has to be a valid URL.");
            }
            
            String repoFilter = String.format("(&(customer=%s)(name=%s))", customer, name);
            Component storeComponent = m_manager.createComponent()
                .setInterface(new String[]{ RoleRepositoryStore.class.getName(), UserAdminListener.class.getName() }, null)
                .setImplementation(RepositoryBasedRoleRepositoryStore.class)
                .add(m_manager.createServiceDependency()
                    .setService(RemoteRepository.class, repoFilter)
                    .setRequired(true)
                )
                .add(m_manager.createServiceDependency()
                    .setService(LogService.class)
                    .setRequired(false)
                );
            
            m_manager.add(storeComponent);
            m_components.add(storeComponent);
        }
        
    } 

}
