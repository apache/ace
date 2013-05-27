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

import java.util.Map;
import java.util.Set;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * SPI for Management Agent component factories. The create method is called for every individual agent configuration.
 * The factory can return zero or more components and should not add them to the manager. Factories are create and
 * disposed as required. must have a public default constructor, and are expected to be state-less and thread-safe.
 * 
 */
public interface ComponentFactory {

    /**
     * Return zero or more service components for the specified agent configuration.
     * 
     * @param context
     *            The Bundle Context
     * @param manager
     *            The Dependency Manager
     * @param logService
     *            The Log Service
     * @param configuration
     *            The agent configuration
     * @return A set of components, not <code>null</code>
     * @throws Exception
     *             If there is a fatal problem.
     */
    Set<Component> createComponents(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration) throws Exception;
}
