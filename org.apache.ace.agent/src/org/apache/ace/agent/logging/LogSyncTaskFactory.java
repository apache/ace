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
package org.apache.ace.agent.logging;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.spi.ComponentFactoryBase;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Creates a executor whiteboard {@link Runnable} service components with a {@link LogSyncTask} implementation for every
 * configured store unless explicitly disabled.
 * 
 */
public class LogSyncTaskFactory extends ComponentFactoryBase {

    @Override
    public Set<Component> createComponents(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) {

        Set<Component> components = new HashSet<Component>();
        String value = configuration.get(LogFactory.LOG_STORES);
        String[] stores = value.split(",");
        for (String store : stores) {

            String sync = configuration.get(LogFactory.LOG_STORES + "." + store + ".sync");
            if (sync != null && sync.trim().toLowerCase().equals("false")) {
                System.err.println("Disabled " + getAgentIdentifier(configuration) + "/" + store);
                logService.log(LogService.LOG_DEBUG, "Log sync disabled for agent " + getAgentIdentifier(configuration) + "/" + store);
            }
            else {
                components.add(createLogSyncComponent(context, manager, logService, configuration, store.trim()));
            }
        }
        return components;
    }

    private Component createLogSyncComponent(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration, String store) {

        Properties props = getAgentproperties(configuration);
        props.put(LogFactory.LOG_NAME, store);

        props.put(SchedulerConstants.SCHEDULER_NAME_KEY, LogSyncTask.class.getSimpleName());
        props.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes log store " + store + " for agent=" + getAgentIdentifier(configuration) + " on the target and server");
        props.put(SchedulerConstants.SCHEDULER_RECIPE, "2000");

        Component component = manager.createComponent()
            .setInterface(Runnable.class.getName(), props)
            .setImplementation(new LogSyncTask(store))
            .add(manager.createServiceDependency()
                .setService(ConnectionFactory.class, getAgentFilter(configuration, null))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(LogStore.class, getAgentFilter(configuration, "(" + LogFactory.LOG_NAME + "=" + store + ")"))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(Discovery.class, getAgentFilter(configuration, null))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(Identification.class, getAgentFilter(configuration, null))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(LogService.class).setRequired(false));

        return component;
    }
}
