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
package org.apache.ace.processlauncher.osgi;

import java.util.Properties;

import org.apache.ace.processlauncher.ProcessLauncherService;
import org.apache.ace.processlauncher.impl.ProcessLauncherServiceImpl;
import org.apache.ace.processlauncher.impl.ProcessManager;
import org.apache.ace.processlauncher.impl.ProcessManagerImpl;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * Provides the actual bundle activator (based on Felix Dependency Manager).
 */
public class Activator extends DependencyActivatorBase {

    private ProcessLauncherServiceImpl m_processLauncherService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        m_processLauncherService.shutdown();
        m_processLauncherService = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        // In the future we might want to publish this as an external service...
        ProcessManager processManager = new ProcessManagerImpl();

        manager.add(createComponent().setImplementation(processManager).add(
            createServiceDependency().setService(LogService.class).setRequired(false)));

        // We publish the service under multiple interfaces...
        String[] interfaces = { ManagedServiceFactory.class.getName(), ProcessLauncherService.class.getName() };

        // Service properties
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, ProcessLauncherService.PID);

        m_processLauncherService = new ProcessLauncherServiceImpl();
        m_processLauncherService.setProcessManager(processManager);

        manager.add(createComponent().setInterface(interfaces, props).setImplementation(m_processLauncherService)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }
}
