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
package org.apache.ace.resourceprocessor.useradmin.impl;

import java.util.Properties;

import org.apache.ace.resourceprocessor.useradmin.UserAdminConfigurator;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Activator for the UserAdmin resource processor. The services of this bundle will be published as a
 * UserAdminConfigurator, and a ResourceProcessor for use by the Deployment Admin.
 */
public class Activator extends DependencyActivatorBase {
    private static final String PID = "org.apache.ace.resourceprocessor.useradmin";

    @Override
    public void init(BundleContext context, DependencyManager manager) {
        UserAdminStore userAdminStore = new UserAdminStore(context);
        Processor processor = new Processor(userAdminStore);

        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID);
        manager.add(createComponent().setInterface(ResourceProcessor.class.getName(), props)
            .setImplementation(processor)
            .add(createServiceDependency()
                .setService(UserAdminConfigurator.class)
                .setRequired(true)) // This UserAdminConfigurator is the same as below,
                                    // and we don't want to add UserAdmins twice.
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));

        manager.add(createComponent().setInterface(UserAdminConfigurator.class.getName(), null)
            .setImplementation(userAdminStore)
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setAutoConfig(false)
                .setCallbacks("userAdminAdded", "userAdminRemoved"))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}
