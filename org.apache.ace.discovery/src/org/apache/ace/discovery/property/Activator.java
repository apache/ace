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
package org.apache.ace.discovery.property;

import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.discovery.DiscoveryConstants;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {
    private static final String MA_NAME = "ma";
    
    private final Map<String, Component> m_instances = new HashMap<>();
    private DependencyManager m_manager;

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary<Object, Object> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, DiscoveryConstants.DISCOVERY_PID);
        
        manager.add(createComponent()
            .setInterface(new String[] {Discovery.class.getName()}, properties)
            .setImplementation(PropertyBasedDiscovery.class)
            .add(createConfigurationDependency()
                .setPid(DiscoveryConstants.DISCOVERY_PID))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), new Properties() {{ put(Constants.SERVICE_PID, DiscoveryConstants.DISCOVERY_FACTORY_PID); }})
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false))
            );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
    
    public String getName() {
        return "Discovery Service Factory";
    }

    public void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String ma = (String) dict.get(MA_NAME);
        String id = (String) dict.get(DiscoveryConstants.DISCOVERY_URL_KEY);
        
        if (id == null) {
            throw new ConfigurationException(DiscoveryConstants.DISCOVERY_URL_KEY, "Must be specified");
        }

        boolean needToAddComponent = false;
        Component component;
        synchronized (m_instances) {
            component = (Component) m_instances.get(pid);
            if (component == null) {
                Properties props = new Properties();
                if ((ma != null) && (ma.length() > 0)) {
                    props.put(MA_NAME, ma);
                }
                props.put(DiscoveryConstants.DISCOVERY_URL_KEY, id);
                component = m_manager.createComponent()
                    .setInterface(Discovery.class.getName(), props)
                    .setImplementation(new PropertyBasedDiscovery(id))
                    .add(createServiceDependency()
                        .setService(LogService.class)
                        .setRequired(false)
                    );
                m_instances.put(pid, component);
                needToAddComponent = true;
            }
        }
        if (needToAddComponent) {
            m_manager.add(component);
        }
        else {
            Object service = component.getInstance();
            if (service instanceof PropertyBasedDiscovery) {
                PropertyBasedDiscovery identification = (PropertyBasedDiscovery) service;
                try {
                    identification.setURL(id);
                }
                catch (MalformedURLException e) {
                    throw new ConfigurationException(DiscoveryConstants.DISCOVERY_URL_KEY, "Malformed URL", e);
                }
            }
        }
    }

    public void deleted(String pid) {
        Component log;
        synchronized (m_instances) {
            log = (Component) m_instances.remove(pid);
        }
        if (log != null) {
            m_manager.remove(log);
        }
    }
}