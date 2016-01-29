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
package org.apache.ace.log.server.store.impl;

import java.io.File;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.log.server.store.LogStore;
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
    public static final String PID = "org.apache.ace.log.server.store.filebased";

    private static final String LOG_NAME = "name";
    private DependencyManager m_manager;
    private final Map<String, Component> m_instances = new HashMap<>();
    private BundleContext m_context;
    private volatile LogService m_log;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_context = context;
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "org.apache.ace.log.server.store.factory");
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public void deleted(String pid) {
        Component log = m_instances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
            delete(new File(m_context.getDataFile(""), pid));
        }
    }

    private void delete(File root) {
        if (root.isDirectory()) {
            for (File file : root.listFiles()) {
                delete(file);
            }
        }
        root.delete();
    }

    public String getName() {
        return "Log Store Factory";
    }

    public synchronized void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Component service = m_instances.get(pid);
        if (service == null) {
            Properties props = new Properties();
            props.put(LOG_NAME, name);
            File baseDir = new File(m_context.getDataFile(""), pid);
            service = m_manager.createComponent()
                .setInterface(LogStore.class.getName(), props)
                .setImplementation(new LogStoreImpl(baseDir, name))
                .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
                .add(createConfigurationDependency().setPid(PID));
            m_instances.put(pid, service);
            m_manager.add(service);
        } else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }
}