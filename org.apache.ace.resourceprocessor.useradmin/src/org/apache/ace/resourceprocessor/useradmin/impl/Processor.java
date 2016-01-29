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
package org.apache.ace.resourceprocessor.useradmin.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.osgi.service.deploymentadmin.spi.DeploymentSession;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.deploymentadmin.spi.ResourceProcessorException;
import org.osgi.service.log.LogService;

/**
 * Implementation of the ResourceProcessor. This base class takes care of the interaction with the DeploymentAdmin,
 * while delegating the 'actual' work to a store.
 */
public class Processor implements ResourceProcessor {
    private volatile LogService m_log; /* Injected by dependency manager */

    private volatile DeploymentSession m_session;
    private String m_deploymentPackageName;
    private List<String> m_toInstall;
    private List<String> m_toRemove;

    private final UserAdminStore m_resourceStore;

    Processor(UserAdminStore store) {
        m_resourceStore = store;
    }

    /**
     * Sets up the necessary environment for a deployment session.
     */
    private void startSession(DeploymentSession session) {
        if (m_session != null) {
            throw new IllegalArgumentException("This resource processor is currently processing another deployment session, installing deploymentpackage" + m_session.getTargetDeploymentPackage().getName());
        }
        m_session = session;
        m_toInstall = new ArrayList<>();
        m_toRemove = new ArrayList<>();

        String fromSource = session.getSourceDeploymentPackage().getName();
        String fromTarget = session.getTargetDeploymentPackage().getName();
        if (fromSource.equals("")) {
            m_deploymentPackageName = fromTarget;
        }
        else {
            m_deploymentPackageName = fromSource;
        }
    }

    /**
     * Ends a deployment session.
     */
    private void endSession() {
        m_session = null;
        m_deploymentPackageName = null;
        m_toInstall = null;
        m_toRemove = null;
    }

    private void ensureSession() {
        if (m_session == null) {
            throw new IllegalStateException("This resource processor is currently not part of a deployment session.");
        }
    }

    public void begin(DeploymentSession session) {
        startSession(session);
    }

    public void process(String name, InputStream stream) throws ResourceProcessorException {
        ensureSession();
        String originalDeploymentPackage = m_resourceStore.getDeploymentPackage(name);
        if ((originalDeploymentPackage != null) && !m_deploymentPackageName.equals(originalDeploymentPackage)) {
            throw new ResourceProcessorException(ResourceProcessorException.CODE_RESOURCE_SHARING_VIOLATION, "Resource " + name + " does not belong to deployment package " + m_deploymentPackageName + ", but to " + originalDeploymentPackage);
        }

        try {
            m_resourceStore.addResource(m_deploymentPackageName, name, stream);
        }
        catch (IOException e) {
            throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Error storing resource.", e);
        }

        m_toInstall.add(name);
    }

    public void dropped(String resource) throws ResourceProcessorException {
        ensureSession();
        m_toRemove.add(resource);
    }

    public void dropAllResources() throws ResourceProcessorException {
        ensureSession();
        m_toRemove.addAll(m_resourceStore.getResources(m_deploymentPackageName));
    }

    public void prepare() throws ResourceProcessorException {
        ensureSession();
    }

    public void commit() {
        ensureSession();
        m_resourceStore.begin();

        while (!m_toInstall.isEmpty()) {
            try {
                m_resourceStore.install(m_toInstall.remove(0));
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_ERROR, "Error occurred installing resource", ioe);
            }
        }
        while (!m_toRemove.isEmpty()) {
            try {
                m_resourceStore.uninstall(m_toRemove.remove(0));
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_ERROR, "Error occurred removing resource", ioe);
            }
        }

        m_resourceStore.end();
        endSession();
    }

    public void rollback() {
        // nothing special to do.
        ensureSession();
        endSession();
    }

    public void cancel() {
        ensureSession();
        // Nothing to do: we have no long-running operation, we only read the stream.
    }

}
