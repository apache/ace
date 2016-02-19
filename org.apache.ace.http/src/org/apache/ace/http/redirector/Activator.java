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
package org.apache.ace.http.redirector;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Servlet;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {
    private static final String PID = "org.apache.ace.http.redirector.factory";
    private final ConcurrentHashMap<String, Component> m_servlets = new ConcurrentHashMap<>();
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties properties = new Properties();
        properties.put(Constants.SERVICE_PID, PID);
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), properties)
            .setImplementation(this)
        );
    }

    @Override
    public String getName() {
        return "Http Redirector";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        Component component = m_servlets.get(pid);
        if (component == null) {
            component = getDependencyManager().createComponent()
                .setInterface(Servlet.class.getName(), properties)
                .setImplementation(new RedirectServlet(properties))
                ;
            m_servlets.put(pid, component);
            getDependencyManager().add(component);
        }
        else {
            RedirectServlet servlet = (RedirectServlet) component.getInstance();
            if (servlet != null) {
                servlet.update(properties);
            }
        }
    }

    @Override
    public void deleted(String pid) {
        Component component = m_servlets.remove(pid);
        if (component != null) {
            getDependencyManager().remove(component);
        }
    }
}
