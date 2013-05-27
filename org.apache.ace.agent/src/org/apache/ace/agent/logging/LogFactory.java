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

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.spi.ComponentFactoryBase;
import org.apache.ace.log.Log;
import org.apache.ace.log.target.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Creates {@link Log} service components with a {@link LogImpl} implementation for every configured store.
 * 
 */
public class LogFactory extends ComponentFactoryBase {

    public static final String LOG_STORES = "logstores";
    public static final String LOG_NAME = "name";

    @Override
    public Set<Component> createComponents(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration) {

        Set<Component> components = new HashSet<Component>();
        String value = configuration.get(LOG_STORES);
        String[] stores = value.split(",");
        for (String store : stores) {
            components.add(createLogComponent(context, manager, logService, configuration, store.trim()));
        }
        return components;
    }

    private Component createLogComponent(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration, String store) {

        Properties properties = getAgentproperties(configuration);
        properties.put("name", store);

        return manager.createComponent()
            .setInterface(Log.class.getName(), properties)
            .setImplementation(new LogImpl())
            .add(manager.createServiceDependency()
                .setService(LogStore.class, getAgentFilter(configuration, "(name=" + store + ")"))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(LogService.class)
                .setRequired(false));
    }
}
