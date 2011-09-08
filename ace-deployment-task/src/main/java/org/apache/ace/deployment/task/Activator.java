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

import org.apache.ace.deployment.Deployment;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
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
    private static final String MA_NAME = "ma";
    private DependencyManager m_manager;
    private final Map<String, List<Component>> m_instances = new HashMap<String, List<Component>>();
    private BundleContext m_context;

    
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_context = context;
        m_manager = manager;
        List<Component> components = createServices(null);
        for (Component component : components) {
            m_manager.add(component);
        }
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), new Properties() {{ put(Constants.SERVICE_PID, "org.apache.ace.deployment.factory"); }} )
            .setImplementation(this)
        );
    }

    private List<Component> createServices(String ma) {
        List<Component> result = new ArrayList<Component>();
        Dictionary updateProperties = new Properties();
        Dictionary checkProperties = new Properties();
        Dictionary deploymentProperties = new Properties();
        String updateSchedulerName;
        String updateDescription;
        String checkSchedulerName;
        String checkDescription;
        String identificationFilter;
        String discoveryFilter;
        String deploymentFilter;

        if (ma == null) {
            updateSchedulerName = DeploymentUpdateTask.class.getName();
            updateDescription = "Task that synchronizes the artifacts (bundles, resources) installed on this target with the server.";
            checkSchedulerName = DeploymentCheckTask.class.getName();
            checkDescription = "Task that checks for updates of artifacts installed on this target with the server.";
            identificationFilter = "(&("+Constants.OBJECTCLASS+"="+Identification.class.getName()+")(!(ma=*)))";
            discoveryFilter = "(&("+Constants.OBJECTCLASS+"="+Discovery.class.getName()+")(!(ma=*)))";
            deploymentFilter = "(&("+Constants.OBJECTCLASS+"="+DeploymentService.class.getName()+")(!(ma=*)))";
        }
        else {
            updateSchedulerName = "ma=" + ma + ";name=" + DeploymentUpdateTask.class.getName();
            updateDescription = "Task that synchronizes the artifacts (bundles, resources) installed on this target with the server with ma=" + ma + ".";
            checkSchedulerName = "ma=" + ma + ";name=" + DeploymentCheckTask.class.getName();
            checkDescription = "Task that checks for updates of artifacts installed on this target with the server with ma=" + ma + ".";
            identificationFilter = "(&("+Constants.OBJECTCLASS+"="+Identification.class.getName()+")(ma=" + ma + "))";
            discoveryFilter = "(&("+Constants.OBJECTCLASS+"="+Discovery.class.getName()+")(ma=" + ma + "))";
            deploymentFilter = "(&("+Constants.OBJECTCLASS+"="+DeploymentService.class.getName()+")(ma=" + ma + "))";
            updateProperties.put(MA_NAME, ma);
            checkProperties.put(MA_NAME, ma);
            deploymentProperties.put(MA_NAME, ma);
        }
        updateProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, updateSchedulerName);
        updateProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, updateDescription);
        updateProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");

        checkProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, checkSchedulerName);
        checkProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, checkDescription);
        checkProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");
        
        DeploymentTaskBase task = new DeploymentTaskBase();
        DeploymentUpdateTask updateTask = new DeploymentUpdateTask(task);
        DeploymentCheckTask checkTask = new DeploymentCheckTask(task);

        Component deploymentServiceComponent = createComponent()
            .setInterface(DeploymentService.class.getName(), deploymentProperties)
            .setImplementation(task)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class, identificationFilter).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class, discoveryFilter).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        Component updateTaskComponent = createComponent()
            .setInterface(Runnable.class.getName(), updateProperties)
            .setImplementation(updateTask)
            .add(createServiceDependency().setService(DeploymentService.class, deploymentFilter).setRequired(true).setAutoConfig(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        Component checkTaskComponent = createComponent()
            .setInterface(Runnable.class.getName(), checkProperties)
            .setImplementation(checkTask)
            .add(createServiceDependency().setService(DeploymentService.class, deploymentFilter).setRequired(true).setAutoConfig(false))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));

        result.add(deploymentServiceComponent);
        result.add(updateTaskComponent);
        result.add(checkTaskComponent);

        return result;
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    public String getName() {
        return "Deployment Service";
    }

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
}