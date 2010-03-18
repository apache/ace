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
package org.apache.ace.server;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.services.CheckoutService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class CheckoutServiceImpl extends RemoteServiceServlet implements CheckoutService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 302748031613265624L;
    
    
    private final Set<RepositoryAdmin> m_loggedIn = new HashSet<RepositoryAdmin>();

    /**
     * Gets a repository admin for this session. If this admin is not yet logged in,
     * this will be done automatically.
     */
    private RepositoryAdmin getRepositoryAdmin() throws Exception {
        RepositoryAdmin admin = Activator.getService(getThreadLocalRequest(), RepositoryAdmin.class);
        if (!m_loggedIn.contains(admin)) {
            UserAdmin userAdmin = Activator.getService(UserAdmin.class);
            RepositoryAdminLoginContext context = admin.createLoginContext((User) userAdmin.getRole("d"));
            context.addShopRepository(new URL("http://localhost:8080/repository"), "apache", "shop", true);
            context.addGatewayRepository(new URL("http://localhost:8080/repository"), "apache", "gateway", true);
            context.addDeploymentRepository(new URL("http://localhost:8080/repository"), "apache", "deployment", true);
            admin.login(context);
            m_loggedIn.add(admin);
        }
        return admin;
    }

    public void checkout() throws Exception {
        try {
            getRepositoryAdmin().checkout();
        }
        catch (Exception e) {
            Activator.instance().getLog().log(LogService.LOG_WARNING, "Checkout failed", e);
            throw e;
        }
    }

    public void commit() throws Exception {
        try {
            getRepositoryAdmin().commit();
        }
        catch (Exception e) {
            Activator.instance().getLog().log(LogService.LOG_WARNING, "Commit failed", e);
            throw e;
        }
    }

    public void revert() throws Exception {
        try {
            getRepositoryAdmin().revert();
        }
        catch (Exception e) {
            Activator.instance().getLog().log(LogService.LOG_WARNING, "Revert failed", e);
            throw e;
        }
    }

}
