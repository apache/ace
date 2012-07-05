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
package org.apache.ace.deployment.task;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String PID_NAME = "org.apache.ace.deployment.task.default.factory";

    private static final String MA_NAME = "ma";
    private static final String DEFAULT_INTERVAL = "5000";

    private final Map<String, List<Component>> m_instances = new HashMap<String, List<Component>>();

    private volatile DependencyManager m_manager;

    /**
     * @see org.osgi.service.cm.ManagedServiceFactory#deleted(java.lang.String)
     */
    public void deleted(String pid) {
        List<Component> components;
        synchronized (m_instances) {
            components = m_instances.remove(pid);
        }
        if (components != null) {
            for (Component component : components) {
                m_manager.remove(component);
            }
        }
    }

    /**
     * @see org.apache.felix.dm.DependencyActivatorBase#destroy(org.osgi.framework.BundleContext, org.apache.felix.dm.DependencyManager)
     */
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    /**
     * @see org.osgi.service.cm.ManagedServiceFactory#getName()
     */
    public String getName() {
        return "Deployment Service - default check/update tasks";
    }

    /**
     * @see org.apache.felix.dm.DependencyActivatorBase#init(org.osgi.framework.BundleContext, org.apache.felix.dm.DependencyManager)
     */
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;

        List<Component> components = createServices(null);
        for (Component component : components) {
            m_manager.add(component);
        }

        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID_NAME);

        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            );
    }

    /**
     * @see org.osgi.service.cm.ManagedServiceFactory#updated(java.lang.String, java.util.Dictionary)
     */
    public void updated(String pid, Dictionary dict) throws ConfigurationException {
        String ma = (String) dict.get(MA_NAME);

        List<Component> components = m_instances.get(pid);
        if (components == null) {
            components = createServices(ma);
            synchronized (m_instances) {
                m_instances.put(pid, components);
            }
            for (Component component : components) {
                m_manager.add(component);
            }
        }
        else {
            // TODO do we want to deal with changes here?
        }
    }

    /**
     * Creates the check/update task components for the given management agent name.
     * 
     * @param ma the name of the management agent to create the service for, can be <code>null</code>.
     * @return an array with {@link Component} instances for the different tasks, never <code>null</code>.
     */
    private List<Component> createServices(String ma) {
        Dictionary updateProperties = new Properties();
        Dictionary checkProperties = new Properties();
        Dictionary deploymentProperties = new Properties();
        
        String updateSchedulerName = DeploymentUpdateTask.class.getName();
        String updateDescription = "Task that synchronizes the artifacts (bundles, resources) installed on this target with the server.";
        
        String checkSchedulerName = DeploymentCheckTask.class.getName();
        String checkDescription = "Task that checks for updates of artifacts installed on this target with the server.";
        
        String deploymentFilter = "(" + Constants.OBJECTCLASS + "=" + DeploymentService.class.getName() + ")";

        if (ma == null || "".equals(ma)) {
            deploymentFilter = String.format("(&%s(!(%s=*)))", deploymentFilter, MA_NAME);
        }
        else {
            updateSchedulerName = "ma=" + ma + ";name=" + updateSchedulerName;
            updateDescription = "Task that synchronizes the artifacts (bundles, resources) installed on this target with the server with ma=" + ma + ".";
            
            checkSchedulerName = "ma=" + ma + ";name=" + checkSchedulerName;
            checkDescription = "Task that checks for updates of artifacts installed on this target with the server with ma=" + ma + ".";
            
            deploymentFilter = String.format("(&%s(%s=%s))", deploymentFilter, MA_NAME, ma);
            
            updateProperties.put(MA_NAME, ma);
            checkProperties.put(MA_NAME, ma);
            deploymentProperties.put(MA_NAME, ma);
        }

        List<Component> result = new ArrayList<Component>();

        updateProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, updateSchedulerName);
        updateProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, updateDescription);
        updateProperties.put(SchedulerConstants.SCHEDULER_RECIPE, DEFAULT_INTERVAL);

        DeploymentUpdateTask updateTask = new DeploymentUpdateTask();

        Component updateTaskComponent =
            createComponent()
                .setInterface(Runnable.class.getName(), updateProperties)
                .setImplementation(updateTask)
                .add(createServiceDependency().setService(DeploymentService.class, deploymentFilter).setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

        checkProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, checkSchedulerName);
        checkProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, checkDescription);
        checkProperties.put(SchedulerConstants.SCHEDULER_RECIPE, DEFAULT_INTERVAL);

        result.add(updateTaskComponent);

        DeploymentCheckTask checkTask = new DeploymentCheckTask();

        Component checkTaskComponent =
            createComponent()
                .setInterface(Runnable.class.getName(), checkProperties)
                .setImplementation(checkTask)
                .add(createServiceDependency().setService(DeploymentService.class, deploymentFilter).setRequired(true))
                .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

        result.add(checkTaskComponent);

        return result;
    }
}