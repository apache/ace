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
package org.apache.ace.authentication.impl;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a bundle activator for the {@link AuthenticationServiceImpl}.
 */
public class Activator extends DependencyActivatorBase {

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
// @formatter:off
        manager.add(createComponent()
            .setInterface(AuthenticationService.class.getName(), null)
            .setImplementation(new AuthenticationServiceImpl())
            .add(createServiceDependency()
                .setRequired(true)
                .setService(UserAdmin.class))
            .add(createServiceDependency()
                .setRequired(false)
                .setService(LogService.class))
            .add(createServiceDependency()
                .setRequired(false)
                .setService(AuthenticationProcessor.class)
                .setCallbacks("addAuthenticationProcessor", "removeAuthenticationProcessor"))
        );
// @formatter:on
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nop
    }
}
