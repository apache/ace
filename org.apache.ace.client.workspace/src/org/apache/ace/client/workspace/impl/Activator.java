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
package org.apache.ace.client.workspace.impl;

import java.util.Properties;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.workspace.WorkspaceManager;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    /**
     * Identifier for configuration settings.
     */
    public static final String WORKSPACE_PID = "org.apache.ace.client.workspace";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(CommandProcessor.COMMAND_SCOPE, "ace");
        props.put(CommandProcessor.COMMAND_FUNCTION, new String[] { "cw", "gw", "rw" });
        manager.add(createComponent().setInterface(WorkspaceManager.class.getName(), props)
            .setImplementation(WorkspaceManagerImpl.class)
            .add(createServiceDependency().setService(SessionFactory.class).setRequired(true))
            .add(createConfigurationDependency().setPropagate(true).setPid(WORKSPACE_PID))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // nothing needs to be explicitly destroyed here at the moment
    }
}
