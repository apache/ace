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
package org.apache.ace.agent.deployment;

import java.util.Dictionary;

import org.apache.ace.agent.spi.OneComponentFactoryBase;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.Deployment;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Creates a {@link DeploymentService} service component with a {@link DeploymentServiceImpl} implementation.
 * 
 */
public class DeploymentServiceFactory extends OneComponentFactoryBase {

    @Override
    public Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) throws ConfigurationException {

        return manager.createComponent()
            .setInterface(DeploymentService.class.getName(), getAgentproperties(configuration))
            .setImplementation(new DeploymentServiceImpl())
            .add(manager.createServiceDependency().setService(Identification.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(Discovery.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(Deployment.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(ConnectionFactory.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(manager.createServiceDependency().setService(LogService.class).setRequired(false));
    }
}
