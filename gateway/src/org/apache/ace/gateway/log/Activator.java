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
package org.apache.ace.gateway.log;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.gateway.log.store.LogStore;
import org.apache.ace.gateway.log.task.LogSyncTask;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.Log;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.service.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String LOG_NAME = "name";

    private DependencyManager m_manager;
    private final Map m_logInstances = new HashMap(); // String -> Service
    private final Map m_syncInstances = new HashMap(); // String -> Service
    private volatile LogService m_log;

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "org.apache.ace.gateway.log.factory");
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public synchronized void deleted(String pid) {
        Service log = (Service) m_logInstances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
        }
        Service sync = (Service) m_syncInstances.remove(pid);
        if (sync != null) {
            m_manager.remove(sync);
        }
    }

    public String getName() {
        return "Log Factory";
    }

    public synchronized void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Service service = (Service) m_logInstances.get(pid);
        if (service == null) {
            // publish log service
            Properties props = new Properties();
            props.put(LOG_NAME, name);
            Service log = m_manager.createComponent()
                .setInterface(Log.class.getName(), props)
                .setImplementation(LogImpl.class)
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

            // publish log sync task service
            Dictionary properties = new Properties();
            properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes log store with id=" + name + " on the gateway and server");
            properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, name);
            properties.put(SchedulerConstants.SCHEDULER_RECIPE, "2000");
            Service sync = m_manager.createComponent()
                .setInterface(Runnable.class.getName(), properties)
                .setImplementation(new LogSyncTask(name))
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true))
                .add(createServiceDependency().setService(Discovery.class).setRequired(true))
                .add(createServiceDependency().setService(Identification.class).setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

            m_logInstances.put(pid, log);
            m_syncInstances.put(pid, sync);
            m_manager.add(log);
            m_manager.add(sync);
        }
        else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was lready configured: " + name);
        }
    }
}
