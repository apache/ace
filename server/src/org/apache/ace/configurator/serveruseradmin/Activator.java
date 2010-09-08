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
package org.apache.ace.configurator.serveruseradmin;

import java.util.Dictionary;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * This bundle configures a single server user, which is to be used until we
 * have a full-fledged user administration system.
 */
public class Activator extends DependencyActivatorBase {

    private final static String TEST_USER = "serverUser";
    private final static String TEST_PASSWORD = "serverPassword";

    private volatile UserAdmin m_userAdmin; /* Injected by dependency manager */
    private volatile LogService m_log;      /* Injected by dependency manager */

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    public synchronized void start() {
        // create users
        createUser(TEST_USER, TEST_PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private User createUser(String username, String password) {
        User user = (User) m_userAdmin.createRole(username, Role.USER);
        if (user != null) {
            Dictionary properties = user.getProperties();
            if (properties != null) {
                properties.put("username", username);
            }
            else {
                m_log.log(LogService.LOG_ERROR, "Could not get properties for " + username);
            }

            Dictionary credentials = user.getCredentials();
            if (credentials != null) {
                credentials.put("password", password);
            }
            else {
                m_log.log(LogService.LOG_ERROR, "Could not get credentials for " + username);
            }
        }
        else {
            try {
                user = (User) m_userAdmin.getRole(username);
                m_log.log(LogService.LOG_WARNING, "User " + username + " already existed.");
            }
            catch (ClassCastException e) {
                m_log.log(LogService.LOG_WARNING, "Role " + username + " already existed (it's no user).");
            }
        }
        return user;
    }
}
