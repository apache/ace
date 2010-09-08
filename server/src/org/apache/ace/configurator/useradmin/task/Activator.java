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
package org.apache.ace.configurator.useradmin.task;

import java.util.Properties;

import org.apache.ace.resourceprocessor.useradmin.UserAdminConfigurator;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Activator for the UserAdmin updater task.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put("taskName", UpdateUserAdminTask.PID);
        props.put("description", "Synchronizes the UserAdmin with the server.");
        manager.add(createComponent()
            .setInterface(Runnable.class.getName(), props)
            .setImplementation(UpdateUserAdminTask.class)
            .add(createServiceDependency().setService(UserAdminConfigurator.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            .add(createConfigurationDependency().setPid(UpdateUserAdminTask.PID))
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do, the runnable will be pulled automatically.
    }

}
