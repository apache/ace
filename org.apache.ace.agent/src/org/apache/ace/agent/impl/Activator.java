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
package org.apache.ace.agent.impl;

import static org.apache.ace.agent.Constants.FACTORY_PID;

import java.util.Properties;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * OSGi {@link BundleActivator} for the Apache ACE ManagementAGent.
 * 
 */
public class Activator extends DependencyActivatorBase {

    private final BundleActivator[] m_activators = new BundleActivator[] {
        new org.apache.ace.connectionfactory.impl.Activator(),
        new org.apache.ace.scheduler.Activator(),
        new org.apache.ace.consolelogger.Activator(),
        new org.apache.felix.deploymentadmin.Activator()
    };

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        Properties properties = new Properties();
        properties.put(Constants.SERVICE_PID, FACTORY_PID);
        ManagementAgentFactory factory = new ManagementAgentFactory();
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), properties)
            .setImplementation(factory)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));

        StaticConfigurationHandler handler = new StaticConfigurationHandler();
        manager.add(createComponent()
            .setImplementation(handler)
            .add(createServiceDependency().setService(ManagedServiceFactory.class, "(" + Constants.SERVICE_PID + "=" + FACTORY_PID + ")").setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
