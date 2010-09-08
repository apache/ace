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
package org.apache.ace.client.repository.stateful.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.client.repository.RepositoryAdmin;
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
import org.apache.ace.server.log.store.LogStore;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * Activator for the StatefulGatewayRepository bundle.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        StatefulGatewayRepositoryImpl statefulGatewayRepositoryImpl = new StatefulGatewayRepositoryImpl();
        manager.add(createComponent()
            .setInterface(StatefulGatewayRepository.class.getName(), null)
            .setImplementation(statefulGatewayRepositoryImpl)
            .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(false))
            .add(createServiceDependency().setService(BundleHelper.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
        Dictionary<String, String[]> topic = new Hashtable<String, String[]>();
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
        manager.add(createComponent()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(statefulGatewayRepositoryImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // service deregistration will happen automatically.
    }
}
