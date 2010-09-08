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
package org.apache.ace.server.log;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServlet;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.server.log.store.LogStore;
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

    private final Map<String, Service> m_instances = new HashMap<String, Service>(); // String -> Service
    private DependencyManager m_manager;
    private volatile LogService m_log;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "org.apache.ace.server.log.servlet.factory");
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public void deleted(String pid) {
        Service log = m_instances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
        }
    }

    public String getName() {
        return "Log Servlet Factory";
    }

    @SuppressWarnings("unchecked")
    public void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }
        String endpoint = (String) dict.get(HttpConstants.ENDPOINT);
        if ((endpoint == null) || "".equals(endpoint)) {
            throw new ConfigurationException(HttpConstants.ENDPOINT, "Servlet endpoint has to be specified.");
        }

        Service service = m_instances.get(pid);
        if (service == null) {
            Properties props = new Properties();
            props.put(HttpConstants.ENDPOINT, endpoint);
            service = m_manager.createComponent()
                .setInterface(HttpServlet.class.getName(), props)
                .setImplementation(new LogServlet(name))
                .add(createServiceDependency().setService(LogService.class).setRequired(false))
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true));

            m_instances.put(pid, service);
            m_manager.add(service);
        } else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }
}
