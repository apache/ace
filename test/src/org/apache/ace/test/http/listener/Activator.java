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
package org.apache.ace.test.http.listener;

import org.apache.ace.test.osgi.dm.TestActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;

/**
 * Activator for the integration test.
 */
public class Activator extends TestActivatorBase {

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createComponent()
            .setImplementation(ServletConfiguratorIntegrationTest.class)
            .add(createServiceDependency()
                .setService(HttpService.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false))
                );
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return new Class[] { ServletConfiguratorIntegrationTest.class };
    }
}
