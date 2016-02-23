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
package org.apache.ace.http.context;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;

import java.util.Properties;

import org.apache.ace.http.HttpConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    private static final String PID = "org.apache.ace.http.context";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(HTTP_WHITEBOARD_CONTEXT_NAME, HttpConstants.ACE_WHITEBOARD_CONTEXT_NAME);

        manager.add(createComponent()
            .setInterface(ServletContextHelper.class.getName(), props)
            .setImplementation(AceServletContextHelper.class)
            .add(createConfigurationDependency().setPid(PID))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

}
