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
package org.apache.ace.discovery.property;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.discovery.property.constants.DiscoveryConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary properties = new Hashtable();
        properties.put(Constants.SERVICE_PID, DiscoveryConstants.DISCOVERY_PID);
        manager.add(createComponent()
                        .setInterface(new String[] {Discovery.class.getName()}, properties)
                        .setImplementation(PropertyBasedDiscovery.class)
                        .add(createConfigurationDependency()
                            .setPid(DiscoveryConstants.DISCOVERY_PID))
                        .add(createServiceDependency()
                            .setService(LogService.class)
                            .setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}