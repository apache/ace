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
package org.apache.ace.test.repositoryadmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.osgi.dm.TestActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;

/**
 * Activator for the integration test.
 */
public class Activator extends TestActivatorBase {
    private volatile ConfigurationAdmin m_configAdmin;
    private volatile SessionFactory m_sessionFactory;

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency().setService(SessionFactory.class).setRequired(true))
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true)));

        Dictionary<String, Object> topics = new Hashtable<String, Object>();
        topics.put(EventConstants.EVENT_TOPIC, new String[] {RepositoryObject.PUBLIC_TOPIC_ROOT + "*",
            RepositoryObject.PRIVATE_TOPIC_ROOT + "*",
            RepositoryAdmin.PUBLIC_TOPIC_ROOT + "*",
            RepositoryAdmin.PRIVATE_TOPIC_ROOT + "*",
            StatefulGatewayObject.TOPIC_ALL});
        manager.add(createComponent()
            .setInterface(EventHandler.class.getName(), topics)
            .setImplementation(RepositoryAdminTest.class)
            .add(createServiceDependency().setService(HttpService.class).setRequired(true))
            .add(createServiceDependency().setService(RepositoryAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
            .add(createServiceDependency().setService(Artifact2GroupAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GroupRepository.class).setRequired(true))
            .add(createServiceDependency().setService(Group2LicenseAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LicenseRepository.class).setRequired(true))
            .add(createServiceDependency().setService(License2GatewayAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
            .add(createServiceDependency().setService(StatefulGatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true)));
    }

    public void start() throws IOException {
        Properties props = new Properties();
        props.put("name", "auditlog");
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.log.store.factory", null);
        config.update(props);

        m_sessionFactory.createSession("test-session-ID");
    }

    public void stop() {
        m_sessionFactory.destroySession("test-session-ID");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return new Class[] { RepositoryAdminTest.class };
    }
}

