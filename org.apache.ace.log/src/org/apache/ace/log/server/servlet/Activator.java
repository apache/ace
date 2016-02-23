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
package org.apache.ace.log.server.servlet;

import static org.apache.ace.http.HttpConstants.ACE_WHITEBOARD_CONTEXT_SELECT_FILTER;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Servlet;

import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {
    private static final String PID = "org.apache.ace.log.server.servlet.factory";

    private static final String KEY_LOG_NAME = "name";
    private static final String KEY_ENDPOINT = "endpoint";

    private final ConcurrentMap<String, Component> m_instances = new ConcurrentHashMap<>();
    // Managed by Felix DM...
    private volatile DependencyManager m_manager;
    private volatile LogService m_log;

    public void deleted(String pid) {
        Component log = m_instances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
        }
    }

    public String getName() {
        return "Log Servlet Factory";
    }

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID);

        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String name = (String) dict.get(KEY_LOG_NAME);
        if (name == null || "".equals(name.trim())) {
            throw new ConfigurationException(KEY_LOG_NAME, "Log name has to be specified!");
        }

        String endpoint = (String) dict.get(KEY_ENDPOINT);
        if (endpoint == null || "".equals(endpoint.trim())) {
            throw new ConfigurationException(KEY_ENDPOINT, "Endpoint name must be specified!");
        }

        Properties servletProps = new Properties();
        servletProps.put(HTTP_WHITEBOARD_SERVLET_PATTERN, toPattern(endpoint));
        servletProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WHITEBOARD_CONTEXT_SELECT_FILTER);
        servletProps.put(KEY_LOG_NAME, name);

        Component service = m_instances.get(pid);
        if (service == null) {
            service = m_manager.createComponent()
                .setInterface(Servlet.class.getName(), servletProps)
                .setImplementation(new LogServlet(name))
                .add(createServiceDependency().setService(LogService.class).setRequired(false))
                .add(createServiceDependency().setService(LogStore.class, "(name=" + name + ")").setRequired(true));

            if (m_instances.putIfAbsent(pid, service) == null) {
                m_manager.add(service);
            }
        }
        else {
            m_log.log(LogService.LOG_WARNING, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }

    private Object toPattern(String endpoint) {
        final String suffix = "/*";
        if ("/".equals(endpoint)) {
            return suffix;
        }
        if (!endpoint.endsWith(suffix)) {
            return endpoint.concat(suffix);
        }
        return endpoint;
    }
}
