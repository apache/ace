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
package org.apache.ace.deployment.servlet;

import static org.apache.ace.http.HttpConstants.ACE_WHITEBOARD_CONTEXT_SELECT_FILTER;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.util.Properties;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.processor.DeploymentProcessor;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String AGENT_PID = "org.apache.ace.deployment.servlet.agent";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties deploymentServletProps = new Properties();
        deploymentServletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/deployment/*");
        deploymentServletProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WHITEBOARD_CONTEXT_SELECT_FILTER);
        
        manager.add(createComponent()
            .setInterface(Servlet.class.getName(), deploymentServletProps)
            .setImplementation(DeploymentServlet.class)
            .add(createServiceDependency().setService(StreamGenerator.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentProvider.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentProcessor.class).setRequired(false).setCallbacks("addProcessor", "removeProcessor"))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
        );
         
        Properties agentServletProps = new Properties();
        agentServletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, "/agent/*");
        agentServletProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WHITEBOARD_CONTEXT_SELECT_FILTER);
        
        manager.add(createComponent()
            .setInterface(Servlet.class.getName(), agentServletProps)
            .setImplementation(AgentDeploymentServlet.class)
            .add(createConfigurationDependency().setPid(AGENT_PID))
            .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
        );
        
        Properties filterProps = new Properties();
        filterProps.put(HTTP_WHITEBOARD_FILTER_PATTERN, "/*");
        manager.add(createComponent()
            .setInterface(Filter.class.getName(), null)
            .setImplementation(OverloadedFilter.class)
        );
    }

}