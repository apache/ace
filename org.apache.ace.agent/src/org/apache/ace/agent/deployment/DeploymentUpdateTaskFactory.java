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

import java.util.Map;
import java.util.Properties;

import org.apache.ace.agent.spi.OneComponentFactoryBase;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Creates a executor whiteboard {@link Runnable} service component with a {@link DeploymentUpdateTask} implementation.
 * 
 */
public class DeploymentUpdateTaskFactory extends OneComponentFactoryBase {

    @Override
    public Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration) throws Exception {

        Properties properties = getAgentproperties(configuration);
        properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentUpdateTask.class.getSimpleName());
        properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes the artifacts (bundles, resources) installed on this target with the server.");
        properties.put(SchedulerConstants.SCHEDULER_RECIPE, 2000);

        return manager.createComponent()
            .setInterface(Runnable.class.getName(), properties)
            .setImplementation(new DeploymentUpdateTask())
            .add(manager.createServiceDependency().setService(DeploymentService.class, getAgentFilter(configuration, null)).setRequired(true))
            .add(manager.createServiceDependency().setService(LogService.class).setRequired(false));
    }

}
