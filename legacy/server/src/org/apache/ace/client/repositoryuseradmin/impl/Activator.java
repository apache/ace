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
package org.apache.ace.client.repositoryuseradmin.impl;

import org.apache.ace.client.repositoryuseradmin.RepositoryUserAdmin;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the Repository UserAdmin. Note that this UserAdmin is not intended
 * to be a full implementation of the UserAdmin specification, but rather a
 * value-object model that uses the UserAdmin interface for convenience.
 */
public class Activator extends DependencyActivatorBase {

    RepositoryUserAdminImpl m_impl;

    @Override
    public void init(BundleContext context, DependencyManager manager) {
        m_impl = new RepositoryUserAdminImpl();
        manager.add(createComponent()
            .setInterface(RepositoryUserAdmin.class.getName(), null)
            .setImplementation(m_impl)
            .add(createServiceDependency()
                 .setService(PreferencesService.class)
                 .setRequired(true))
            .add(createServiceDependency()
                 .setService(LogService.class)
                 .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // At least, save our progress.
        m_impl.logout(true);
    }
}
