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
package org.apache.ace.client.automation;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Bundle activator for the target operator automation.
 */
public class Activator extends DependencyActivatorBase {
    /**
     * Initialize and set dependencies
     */
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(AutoTargetOperator.class)
            .add(createConfigurationDependency().setPid(AutoTargetOperator.PID))
            .add(createServiceDependency().setRequired(true).setService(UserAdmin.class))
            .add(createServiceDependency().setRequired(true).setService(TargetRepository.class)) // TODO is this still used?
            .add(createServiceDependency().setRequired(true).setService(StatefulTargetRepository.class))
            .add(createServiceDependency().setRequired(true).setService(DeploymentVersionRepository.class))
            .add(createServiceDependency().setRequired(true).setService(RepositoryAdmin.class))
            .add(createServiceDependency().setRequired(false).setService(LogService.class))
        );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}