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
package org.apache.ace.agent.spi;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

/**
 * Convenience base class for component factories that return just one component.
 * 
 */
public abstract class OneComponentFactoryBase extends ComponentFactoryBase {

    @Override
    public final Set<Component> createComponents(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) throws ConfigurationException {
        Component component = createComponent(context, manager, logService, configuration);
        if (component != null) {
            Set<Component> components = new HashSet<Component>();
            components.add(component);
            return components;
        }
        return Collections.emptySet();
    }

    /**
     * Returns a component for the specified agent configuration.
     * 
     * @param context
     *            The Bundle Context
     * @param manager
     *            The Dependency manager
     * @param logService
     *            The Log Service
     * @param configuration
     *            The agent configuration
     * @return A component, or <code>null</code>
     * @throws ConfigurationException
     *             If there is a fatal problem
     */
    public abstract Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Dictionary<String, String> configuration) throws ConfigurationException;
}
