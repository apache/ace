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
package org.apache.ace.log.target;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.apache.ace.log.target.task.LogSyncTask;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class LogSyncConfigurator implements ManagedServiceFactory {
    private static final String MA_NAME = "ma";
    private static final String LOG_NAME = "name";

    private DependencyManager m_manager;
    private final Map /*<String, Component>*/ m_syncInstances = new HashMap();
    private volatile LogService m_log;
    
    public String getName() {
        return "Log Sync Factory";
    }

    public synchronized void deleted(String pid) {
        Component sync = (Component) m_syncInstances.remove(pid);
        if (sync != null) {
            m_manager.remove(sync);
        }
    }

    public synchronized void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        String ma = (String) dict.get(MA_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Component service = (Component) m_syncInstances.get(pid);
        if (service == null) {
            // publish log sync task service
            Dictionary properties = new Properties();
            String filterString;
            String filterForDiscovery;
            String filterForIdentification;
            String schedulerName;
            String description;
            if (ma == null || "".equals(ma)) {
                filterString = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=" + name + "))";
                filterForDiscovery = "(&(" + Constants.OBJECTCLASS + "=" + Discovery.class.getName() + ")(!(ma=*)))";
                filterForIdentification = "(&(" + Constants.OBJECTCLASS + "=" + Identification.class.getName() + ")(!(ma=*)))";
                schedulerName = name;
                description = "Task that synchronizes log store with id=" + name + " on the target and server";
            }
            else {
                // if there is more than one management agent ("ma" is specified) then still it's very well possible that there's only
                // one log, so either bind to this one global log (assuming ma is not specified for it) or a ma-specific log (ma is
                // specified)
                filterString = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=" + name + ")(|(ma=" + ma + ")(!(ma=*))))";
                filterForDiscovery = "(&(" + Constants.OBJECTCLASS + "=" + Discovery.class.getName() + ")(ma=" + ma + "))";
                filterForIdentification = "(&(" + Constants.OBJECTCLASS+"=" + Identification.class.getName() + ")(ma=" + ma + "))";
                schedulerName = "ma=" + ma + ";name=" + name;
                description = "Task that synchronizes log store with id=" + name + " and ma=" + ma + " on the target and server";
                properties.put(MA_NAME, ma);
            }

            properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, schedulerName);
            properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, description);
            properties.put(SchedulerConstants.SCHEDULER_RECIPE, "2000");
            Component sync = m_manager.createComponent()
                .setInterface(Runnable.class.getName(), properties)
                .setImplementation(new LogSyncTask(name))
                .add(m_manager.createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                .add(m_manager.createServiceDependency().setService(LogStore.class, filterString).setRequired(true))
                .add(m_manager.createServiceDependency().setService(Discovery.class, filterForDiscovery).setRequired(true))
                .add(m_manager.createServiceDependency().setService(Identification.class, filterForIdentification).setRequired(true))
                .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false));

            m_syncInstances.put(pid, sync);
            m_manager.add(sync);
        }
        else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }
}
