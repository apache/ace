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
package org.apache.ace.agent.discovery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.ace.agent.spi.OneComponentFactoryBase;
import org.apache.ace.discovery.Discovery;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

/**
 * Creates a {@link Discovery} service component with an implementation that returns the configured
 * <code>identification.property.value</code>
 * 
 */
public class PropertyBasedDiscoveryFactory extends OneComponentFactoryBase {

    public static final String DISCOVERY_PROPERTY_VALUE = "serverurl";

    @Override
    public Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration) throws ConfigurationException {

        final String urlStr = (String) configuration.get(DISCOVERY_PROPERTY_VALUE);
        if (urlStr == null || urlStr.equals("")) {
            throw new ConfigurationException(DISCOVERY_PROPERTY_VALUE, "Missing a valid discovery value");
        }

        try {
            final URL url = new URL(urlStr);
            Discovery impl = new Discovery() {

                @Override
                public URL discover() {
                    return url;
                }
            };

            return manager.createComponent()
                .setInterface(Discovery.class.getName(), getAgentproperties(configuration)).setImplementation(impl);
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(DISCOVERY_PROPERTY_VALUE, "Discovery URL is bad: " + urlStr);
        }
    }
}
