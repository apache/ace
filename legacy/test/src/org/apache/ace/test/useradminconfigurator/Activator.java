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
package org.apache.ace.test.useradminconfigurator;

import java.io.IOException;
import java.util.Properties;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.osgi.dm.TestActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.useradmin.UserAdmin;

public class Activator extends TestActivatorBase {

    private volatile ConfigurationAdmin m_configAdmin;

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createComponent()
            .setImplementation(ConfiguratorTest.class)
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                .setRequired(true)));

        // We need to do some configuration for this test to run; therefore,
        // we (as activator) wait around for the ConfigurationAdmin.
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(ConfigurationAdmin.class)
                .setRequired(true)));
    }

    @Override
    protected Class[] getTestClasses() {
        return new Class[] { ConfiguratorTest.class };
    }

    public void start() throws IOException {
        // Create the repository
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_NAME, "users");
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, "apache");
        props.put(RepositoryConstants.REPOSITORY_MASTER, "true");

        config.update(props);

        // Start the servlet
        config = m_configAdmin.getConfiguration("org.apache.ace.repository.servlet.RepositoryServlet", null);

        props = new Properties();
        props.put("org.apache.ace.server.servlet.endpoint", "/repository");

        config.update(props);

        // Configure the task
        config = m_configAdmin.getConfiguration("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", null);

        props = new Properties();
        props.put("repositoryName", "users");
        props.put("repositoryCustomer", "apache");
        props.put("repositoryLocation", "http://localhost:" + TestConstants.PORT + "/repository");

        config.update(props);

        // Schedule the task
        config = m_configAdmin.getConfiguration("org.apache.ace.scheduler", null);

        props = new Properties();
        props.put("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", "1000");

        config.update(props);
    }
}
