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
package org.apache.ace.test.log;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.ace.discovery.property.constants.DiscoveryConstants;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.apache.ace.log.Log;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.osgi.dm.TestActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;

public class Activator extends TestActivatorBase {
    private static final String AUDITLOG = "/auditlog";
    private static final String DEPLOYMENT = "/deployment";

    @SuppressWarnings("unchecked")
    private Class[] m_classes = new Class[] { LogIntegrationTest.class };
    public static final String HOST = "localhost";
    public static final String GWID = "gw-id";
    public static final String POLL_INTERVAL = "2";
    private volatile ConfigurationAdmin m_config;
    private volatile LogService m_log;

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return m_classes;
    }

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        // helper service that configures the system
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
        );
        // service containing the actual integration test
        manager.add(createComponent()
            .setImplementation(LogIntegrationTest.class)
            .add(createServiceDependency().setService(HttpService.class).setRequired(true))
            .add(createServiceDependency().setService(Log.class, "(&("+Constants.OBJECTCLASS+"="+Log.class.getName()+")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(Runnable.class, "(&("+Constants.OBJECTCLASS+"="+Runnable.class.getName()+")(taskName=auditlog))").setRequired(true))
        );
    }

    public void start() {
        try {
            configureServer();
            configureGateway();
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Exception while starting", e);
        }
    }

    public void stop() {
        try {
            unconfigureGateway();
            unconfigureServer();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "IO exception while stopping", e);
        }
    }

    private void configureGateway() throws IOException {
        setProperty(DiscoveryConstants.DISCOVERY_PID, new Object[][] { { DiscoveryConstants.DISCOVERY_URL_KEY, "http://" + HOST + ":" + TestConstants.PORT } });
        setProperty(IdentificationConstants.IDENTIFICATION_PID, new Object[][] { { IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, GWID } });
        createFactoryInstance("org.apache.ace.gateway.log.store.factory", new Object[][] { {"name", "auditlog"} });
        createFactoryInstance("org.apache.ace.gateway.log.factory", new Object[][] { {"name", "auditlog"} });
    }

    private void unconfigureGateway() throws IOException {
        m_config.getConfiguration(DiscoveryConstants.DISCOVERY_PID, null).delete();
        m_config.getConfiguration(IdentificationConstants.IDENTIFICATION_PID, null).delete();
    }

    private void unconfigureServer() throws IOException {
        m_config.getConfiguration("org.apache.ace.deployment.servlet", null).delete();
        m_config.getConfiguration("org.apache.ace.deployment.provider.filebased", null).delete();
    }

    private void configureServer() throws Exception {
        setProperty("org.apache.ace.deployment.servlet", new Object[][] { { HttpConstants.ENDPOINT, DEPLOYMENT } });
        createFactoryInstance("org.apache.ace.server.log.servlet.factory", new Object[][] { {"name", "auditlog"}, { HttpConstants.ENDPOINT, AUDITLOG } });
        createFactoryInstance("org.apache.ace.server.log.store.factory", new Object[][] { {"name", "auditlog"} });
    }

    @SuppressWarnings("unchecked")
    private void setProperty(String pid, Object[][] props) throws IOException {
        Configuration configuration = m_config.getConfiguration(pid, null);
        Dictionary dictionary = configuration.getProperties();
        if (dictionary == null) {
            dictionary = new Hashtable();
        }
        for (Object[] pair : props) {
            dictionary.put(pair[0], pair[1]);
        }
        configuration.update(dictionary);
    }

    @SuppressWarnings("unchecked")
    private void createFactoryInstance(String factoryPid, Object[][] props) throws IOException {
        Dictionary dict = new Properties();
        for (Object[] pair : props) {
            dict.put(pair[0], pair[1]);
        }
        Configuration config = m_config.createFactoryConfiguration(factoryPid, null);
        config.update(dict);
    }
}
