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
package org.apache.ace.agent.impl;

import java.util.Properties;

import org.apache.ace.agent.ManagementAgentFactory;
import org.apache.ace.agent.scheduler.impl.Scheduler;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * OSGi {@link BundleActivator} for the Apache ACE ManagementAgent.
 * 
 */
public class Activator extends DependencyActivatorBase {

    private Scheduler m_scheduler;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

        Properties properties = new Properties();
        m_scheduler = new Scheduler();
        manager.add(createComponent()
            .setImplementation(m_scheduler)
            .add(createServiceDependency()
                .setService(LogService.class).setRequired(false))
            .add(createServiceDependency()
                .setService(Runnable.class).setRequired(false)
                .setAutoConfig(false)
                .setCallbacks(this, "addRunnable", "addRunnable", "removeRunnable")));

        properties = new Properties();
        ManagementAgentFactoryImpl factory = new ManagementAgentFactoryImpl();
        manager.add(createComponent()
            .setInterface(ManagementAgentFactory.class.getName(), properties)
            .setImplementation(factory)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));

    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    /**
     * Handler for both adding and updating runnable service registrations.
     * 
     * @throws Exception
     *             Is thrown when the <code>SCHEDULER_RECIPE</code> contained in <code>ref</code>'s service dictionary
     *             cannot be parsed by the scheduler.
     */
    public void addRunnable(ServiceReference ref, Runnable task) throws Exception {
        String name = (String) ref.getProperty(SchedulerConstants.SCHEDULER_NAME_KEY);
        if (name != null) {
            String description = (String) ref.getProperty(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY);
            Object recipe = ref.getProperty(SchedulerConstants.SCHEDULER_RECIPE);
            boolean recipeOverride = Boolean.valueOf((String) ref.getProperty(SchedulerConstants.SCHEDULER_RECIPE_OVERRIDE)).booleanValue();
            m_scheduler.addRunnable(name, task, description, recipe, recipeOverride);
        }
    }

    public synchronized void removeRunnable(ServiceReference ref, Runnable task) {
        String name = (String) ref.getProperty(SchedulerConstants.SCHEDULER_NAME_KEY);
        if (name != null) {
            m_scheduler.removeRunnable(name);
        }
    }

}
