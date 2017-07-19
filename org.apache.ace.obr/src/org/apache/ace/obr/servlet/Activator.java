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
package org.apache.ace.obr.servlet;

import static org.apache.ace.http.HttpConstants.ACE_WHITEBOARD_CONTEXT_SELECT_FILTER;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.util.Properties;

import javax.servlet.Servlet;

import org.apache.ace.obr.storage.BundleStore;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
	private static final String PID = "org.apache.ace.obr.servlet";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties servletProps = new Properties();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, BundleServlet.SERVLET_ENDPOINT.concat("*"));
        servletProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WHITEBOARD_CONTEXT_SELECT_FILTER);
        servletProps.put(Constants.SERVICE_PID, PID);

        String[] ifaces = { Servlet.class.getName(), ManagedService.class.getName() };

        manager.add(createComponent()
            .setInterface(ifaces, servletProps)
            .setImplementation(BundleServlet.class)
            .add(createServiceDependency()
                .setService(BundleStore.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }
}
