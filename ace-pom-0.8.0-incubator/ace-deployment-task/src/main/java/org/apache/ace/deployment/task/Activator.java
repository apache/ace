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
package org.apache.ace.deployment.task;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.deployment.Deployment;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary updateProperties = new Properties();
        updateProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes the artifacts (bundles, resources) installed on this gateway with the server.");
        updateProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentUpdateTask.class.getName());
        updateProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");

        Dictionary checkProperties = new Properties();
        checkProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that checks for updates for gateway on the server.");
        checkProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentCheckTask.class.getName());
        checkProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");

        manager.add(createComponent()
            .setInterface(Runnable.class.getName(), updateProperties)
            .setImplementation(DeploymentUpdateTask.class)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
             .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));

        manager.add(createComponent()
            .setInterface(Runnable.class.getName(), checkProperties)
            .setImplementation(DeploymentCheckTask.class)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
             .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}