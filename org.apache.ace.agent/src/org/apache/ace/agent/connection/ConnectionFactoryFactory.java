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
package org.apache.ace.agent.connection;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.agent.connection.UrlCredentialsFactory.MissingValueException;
import org.apache.ace.agent.spi.OneComponentFactoryBase;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

/**
 * Creates a {@link ConnectionFactory} service component with a {@link ConnectionFactoryImpl} implementation.
 * 
 */
public class ConnectionFactoryFactory extends OneComponentFactoryBase {

    // FIXME This could all be much nicer if we can refactor connectionfactory code some more. Note that at present this
    // only support one credential mapping per agent.
    private final static String[] AUTH_PROPERTIES = new String[] {
        UrlCredentialsFactory.KEY_AUTH_BASE_URL,
        UrlCredentialsFactory.KEY_AUTH_KEYSTORE_FILE,
        UrlCredentialsFactory.KEY_AUTH_KEYSTORE_PASS,
        UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_FILE,
        UrlCredentialsFactory.KEY_AUTH_TRUSTSTORE_PASS,
        UrlCredentialsFactory.KEY_AUTH_TYPE,
        UrlCredentialsFactory.KEY_AUTH_USER_NAME,
        UrlCredentialsFactory.KEY_AUTH_USER_PASSWORD };

    @Override
    public Component createComponent(BundleContext context, DependencyManager manager, LogService logService, Map<String, String> configuration) throws ConfigurationException {

        Properties properties = getAgentproperties(configuration);
        properties.put("impl.type", "jdk");

        String baseUrl = configuration.get(UrlCredentialsFactory.KEY_AUTH_BASE_URL);
        UrlCredentials credentials = null;
        if (baseUrl != null && !"".equals(baseUrl)) {
            Dictionary<String, String> urlCredentials = new Hashtable<String, String>();
            for (String authProp : AUTH_PROPERTIES) {
                if (configuration.get(authProp) != null) {
                    urlCredentials.put(authProp, configuration.get(authProp));
                }
            }

            try {
                credentials = UrlCredentialsFactory.getCredentials(urlCredentials);
            }
            catch (MissingValueException e) {
                throw new ConfigurationException("authorization", e.getMessage(), e);
            }
        }

        return manager.createComponent()
            .setInterface(ConnectionFactory.class.getName(), properties)
            .setImplementation(new ConnectionFactoryImpl(credentials))
            .add(manager.createServiceDependency().setService(LogService.class).setRequired(false));
    }
}
