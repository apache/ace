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
package org.apache.ace.deployment.deploymentadmin;

import java.io.InputStream;

import org.apache.ace.deployment.Deployment;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.log.LogService;

/**
 * Implementation of the <code>DeploymentService</code> interface that uses the <code>DeploymentAdmin</code>
 * to deploy components.
 */
public class DeploymentAdminDeployer implements Deployment {
    private volatile LogService m_log; /* will be injected by dependencymanager */
    private volatile DeploymentAdmin m_admin; /* will be injected by dependencymanager */

    public String getName(Object object) throws IllegalArgumentException {
        if (!(object instanceof DeploymentPackage)) {
            throw new IllegalArgumentException("Argument is not a DeploymentPackage");
        }
        return ((DeploymentPackage) object).getName();
    }

    public Version getVersion(Object object) throws IllegalArgumentException {
        if (!(object instanceof DeploymentPackage)) {
            throw new IllegalArgumentException("Argument is not a DeploymentPackage");
        }
        return ((DeploymentPackage) object).getVersion();
    }

    public Object install(InputStream inputStream) throws Exception {
        DeploymentPackage deploymentPackage = m_admin.installDeploymentPackage(inputStream);
        m_log.log(LogService.LOG_INFO, "Deployment Package installed: name=" + deploymentPackage.getName() + " version=" + deploymentPackage.getVersion());
        return deploymentPackage;
    }

    public Object[] list() {
        // DeploymentAdmin spec says this call should never return null
        return m_admin.listDeploymentPackages();
    }
}
