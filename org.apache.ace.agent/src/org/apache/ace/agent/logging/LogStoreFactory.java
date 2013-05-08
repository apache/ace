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

import java.io.File;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.ace.agent.spi.ComponentFactoryBase;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Creates {@link LogStore} service components with a {@link LogStoreImpl} implementation for every configured store.
 * 
 */
public class LogStoreFactory extends ComponentFactoryBase {

    @Override
    public Set<Component> createComponents(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) {

        Set<Component> components = new HashSet<Component>();
        String value = configuration.get(LogFactory.LOG_STORES);
        String[] stores = value.split(",");
        for (String store : stores) {
            components.add(createLogStoreComponent(context, manager, configuration, logService, store.trim()));
        }
        return components;
    }

    private Component createLogStoreComponent(BundleContext context, DependencyManager manager, Dictionary<String, String> configuration, LogService logService, String store) {

        Properties properties = getAgentproperties(configuration);
        properties.put(LogFactory.LOG_NAME, store);

        File baseDir = new File(context.getDataFile(""), getAgentIdentifier(configuration));

        return manager.createComponent()
            .setInterface(LogStore.class.getName(), properties)
            .setImplementation(new LogStoreImpl(baseDir))
            .add(manager.createServiceDependency()
                .setService(Identification.class, getAgentFilter(configuration, null))
                .setRequired(true))
            .add(manager.createServiceDependency()
                .setService(LogService.class)
                .setRequired(false));
    }
}
