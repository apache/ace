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
package org.apache.ace.ma.webstart;

import java.io.File;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.gateway.log.LogImpl;
import org.apache.ace.gateway.log.store.LogStore;
import org.apache.ace.gateway.log.store.impl.LogStoreImpl;
import org.apache.ace.gateway.log.task.LogSyncTask;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.Log;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;

/**
 * Bundle activator for a completely self-contained management agent that is designed
 * to be used with Java Web Start. It does not use configuration admin and our configurator
 * since we don't have a directory with configuration data when we webstart an application.
 * Instead, we pass on configuration through system properties (that can be put in the
 * JNLP launcher file) and use separate discovery and identification services that pick up
 * on those.
 */
public class Activator extends DependencyActivatorBase {
    private BundleActivator m_deployment;
    private BundleActivator m_task;
    private BundleActivator m_scheduler;
    private BundleActivator m_logListener;
    private BundleActivator m_deploymentAdmin;
    private BundleActivator m_eventAdmin;

    public void start(BundleContext context) throws Exception {
        // start the log listener first so it can listen to as many events as possible
        m_logListener = new org.apache.ace.log.listener.Activator();
        m_logListener.start(context);

        // invoke standard superclass behaviour, which will eventually call our init() method
        super.start(context);

        m_deployment = new org.apache.ace.deployment.deploymentadmin.Activator();
        m_deployment.start(context);
        m_task = new org.apache.ace.deployment.task.Activator();
        m_task.start(context);
        m_eventAdmin = (BundleActivator) Class.forName("org.apache.felix.eventadmin.impl.Activator").newInstance();
        m_eventAdmin.start(context);
        m_deploymentAdmin = (BundleActivator) Class.forName("org.apache.felix.deploymentadmin.Activator").newInstance();
        m_deploymentAdmin.start(context);
        m_scheduler = new org.apache.ace.scheduler.Activator();
        m_scheduler.start(context);
    }

    public void stop(BundleContext context) throws Exception {
        m_deployment.stop(context);
        m_task.stop(context);
        m_scheduler.stop(context);
        m_deploymentAdmin.stop(context);
        m_eventAdmin.stop(context);

        // invoke standard superclass behaviour, which will eventually call our destroy() method
        super.stop(context);

        // stop the log listener as late as possible
        m_logListener.stop(context);
    }

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setInterface(Identification.class.getName(), null)
            .setImplementation(SystemPropertyIdentification.class)
            );

        manager.add(createComponent()
            .setInterface(Discovery.class.getName(), null)
            .setImplementation(SystemPropertyDiscovery.class)
            );

        // we create an audit log store ourselves, since we don't need the flexibility of managed
        // service factories configured by config admin here
        Properties logProps = new Properties();
        logProps.put("name", "auditlog");
        manager.add(createComponent()
            .setInterface(LogStore.class.getName(), logProps)
            .setImplementation(new LogStoreImpl(new File(context.getDataFile(""), "audit")))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );

        // same for the log service and sync task
        manager.add(createComponent()
            .setInterface(Log.class.getName(), logProps)
            .setImplementation(LogImpl.class)
            .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
        Dictionary properties = new Properties();
        properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes audit log store on the gateway and server");
        properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, "auditlog");
        properties.put(SchedulerConstants.SCHEDULER_RECIPE, "2000");

        manager.add(createComponent()
            .setInterface(Runnable.class.getName(), properties)
            .setImplementation(new LogSyncTask("auditlog"))
            .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
