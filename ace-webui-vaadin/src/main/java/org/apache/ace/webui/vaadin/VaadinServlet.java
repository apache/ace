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
package org.apache.ace.webui.vaadin;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.Application;
import com.vaadin.terminal.gwt.server.AbstractApplicationServlet;

public class VaadinServlet extends AbstractApplicationServlet {
	private static final long serialVersionUID = 1L;
	private volatile DependencyManager m_manager;
    
    @Override
    protected Class<? extends Application> getApplicationClass() {
        return VaadinClient.class;
    }

    @Override
    protected Application getNewApplication(HttpServletRequest request)	throws ServletException {
        Application application = new VaadinClient();
        m_manager.add(m_manager.createComponent()
            .setImplementation(application)
            .setCallbacks("setupDependencies", "start", "stop", "destroyDependencies")
            .add(m_manager.createServiceDependency()
                .setService(SessionFactory.class)
                .setRequired(true)
            )
            .add(m_manager.createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true)
            )
            .add(m_manager.createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)
            )
        );
        return application;
    }
}
