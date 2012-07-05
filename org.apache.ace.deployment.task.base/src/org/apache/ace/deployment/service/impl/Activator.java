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
package org.apache.ace.deployment.service.impl;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.Deployment;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Provides an activator for the deployment service.
 */
public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String PID_NAME = "org.apache.ace.deployment.task.base.factory";

    private static final String MA_NAME = "ma";
    private static final String DEFAULT_MA_NAME = null;

    private final Map<String, Component> m_instances = new ConcurrentHashMap<String, Component>();

    private volatile DependencyManager m_manager;

    /**
     * @see org.osgi.service.cm.ManagedServiceFactory#deleted(java.lang.String)
     */
    public void deleted(String pid) {
        Component component;
        synchronized (m_instances) {
            component = m_instances.remove(pid);
        }
        
        if (component != null) {
            m_manager.remove(component);
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
        return "Deployment Service - base";
    }

    /**
     * @see org.apache.felix.dm.DependencyActivatorBase#init(org.osgi.framework.BundleContext, org.apache.felix.dm.DependencyManager)
     */
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;

        // Create a default deployment service instance...
        m_manager.add(createService(DEFAULT_MA_NAME));

        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID_NAME);

        m_manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            );
    }

    /**
     * @see org.osgi.service.cm.ManagedServiceFactory#updated(java.lang.String, java.util.Dictionary)
     */
    public void updated(String pid, Dictionary dict) throws ConfigurationException {
        final String ma = (String) dict.get(MA_NAME);
        
        Component component = m_instances.get(pid);
        if (component == null) {
            component = createService(ma);
            synchronized (m_instances) {
                m_instances.put(pid, component);
            }
            m_manager.add(component);
        }
        else {
            // TODO do we want to deal with changes here?
        }
    }

    /**
     * Creates the {@link DeploymentService} component for the given management agent name.
     * 
     * @param ma the name of the management agent to create the service for, can be <code>null</code>.
     * @return a {@link Component} instance for the {@link DeploymentService}, never <code>null</code>.
     */
    private Component createService(String ma) {
        Dictionary deploymentProperties = new Properties();

        String identificationFilter = "(" + Constants.OBJECTCLASS + "=" + Identification.class.getName() + ")";
        String discoveryFilter = "(" + Constants.OBJECTCLASS + "=" + Discovery.class.getName() + ")";

        if (ma == null || "".equals(ma.trim())) {
            identificationFilter = String.format("(&%s(!(%s=*)))", identificationFilter, MA_NAME);
            discoveryFilter = String.format("(&%s(!(%s=*)))", discoveryFilter, MA_NAME); ;
        }
        else {
            identificationFilter = String.format("(&%s(%s=%s))", identificationFilter, MA_NAME, ma);
            discoveryFilter = String.format("(&%s(%s=%s))", discoveryFilter, MA_NAME, ma);
            deploymentProperties.put(MA_NAME, ma);
        }

        DeploymentServiceImpl deploymentService = new DeploymentServiceImpl();

        return createComponent()
            .setInterface(DeploymentService.class.getName(), deploymentProperties)
            .setImplementation(deploymentService)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class, identificationFilter).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class, discoveryFilter).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false));
    }
}