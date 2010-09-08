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
package org.apache.ace.test.bundlestop;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * This bundle stops the systembundle whenever the deploymentadmin is done deploying.
 */
public class Activator extends DependencyActivatorBase {
    @SuppressWarnings("unchecked")
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        String[] topics = new String[] {EventConstants.EVENT_TOPIC, "org/osgi/service/deployment/COMPLETE"};
        Dictionary properties = new Hashtable();
        properties.put(EventConstants.EVENT_TOPIC, topics);

        SystemBundleStopper stopper = new SystemBundleStopper();
        context.addBundleListener(stopper);
        manager.add(createComponent()
            .setInterface(EventHandler.class.getName(), properties)
            .setImplementation(stopper)
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}

