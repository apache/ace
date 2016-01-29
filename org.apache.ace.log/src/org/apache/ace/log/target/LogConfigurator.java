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

import org.apache.ace.log.Log;
import org.apache.ace.log.target.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class LogConfigurator implements ManagedServiceFactory {
    private static final String LOG_NAME = "name";

    private DependencyManager m_manager;
    private final Map<String, Component> m_logInstances = new HashMap<>();
    private volatile LogService m_log;
    
    public String getName() {
        return "Log Factory";
    }

    public synchronized void deleted(String pid) {
        Component log = (Component) m_logInstances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
        }
    }

    public synchronized void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Component service = (Component) m_logInstances.get(pid);
        if (service == null) {
            // publish log service
            Properties props = new Properties();
            props.put(LOG_NAME, name);
            String filterString;
            filterString = "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=" + name + "))";

            Component log = m_manager.createComponent()
                .setInterface(Log.class.getName(), props)
                .setImplementation(LogImpl.class)
                .add(m_manager.createServiceDependency().setService(LogStore.class, filterString).setRequired(true))
                .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false));

            m_logInstances.put(pid, log);
            m_manager.add(log);
        }
        else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }
}
