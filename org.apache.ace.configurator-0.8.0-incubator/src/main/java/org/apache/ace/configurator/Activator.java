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
package org.apache.ace.configurator;

import java.io.File;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(new Configurator(new File(
                getProperty(context.getProperty(Activator.class.getPackage().getName() + ".CONFIG_DIR"), "conf")),
                getProperty(context.getProperty(Activator.class.getPackage().getName() + ".POLL_INTERVAL"), 2000),
                getProperty(context.getProperty(Activator.class.getPackage().getName() + ".RECONFIG"), true)))
            .add(createServiceDependency()
                .setService(ConfigurationAdmin.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    public String getProperty(String prop, String def) {
        return (prop == null) ? def : prop;
    }

    public long getProperty(String prop, long def) {
        return (prop == null) ? def : Long.parseLong(prop);
    }

    public boolean getProperty(String prop, boolean def) {
        return (prop == null) ? def : Boolean.getBoolean(prop);
    }
}