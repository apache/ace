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
import java.util.Properties;

import org.apache.ace.agent.spi.OneComponentFactoryBase;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Creates a executor whiteboard {@link Runnable} service component with a {@link DeploymentCheckTask} implementation.
 * 
 */
public class DeploymentCheckTaskFactory extends OneComponentFactoryBase {

    @Override
    public Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) throws ConfigurationException {

        Properties properties = getAgentproperties(configuration);
        properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentCheckTask.class.getSimpleName());
        properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that checks for updates of artifacts installed on this target with the server.");
        properties.put(SchedulerConstants.SCHEDULER_RECIPE, 2000);

        return manager.createComponent()
            .setInterface(Runnable.class.getName(), properties)
            .setImplementation(new DeploymentCheckTask())
            .add(manager.createServiceDependency().setService(DeploymentService.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(manager.createServiceDependency().setService(LogService.class).setRequired(false));
    }
}
