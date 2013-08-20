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
package org.apache.ace.agent.impl;

import static org.apache.ace.agent.impl.ReflectionUtil.invokeMethod;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Implementation of the internal agent context service.
 * 
 */
public class AgentContextImpl implements AgentContext {

    // All service are volatile because they may be updated at runtime.
    private volatile AgentControl m_agentControl;
    private volatile ConfigurationHandler m_configurationHandler;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile DeploymentHandler m_deploymentHandler;
    private volatile DownloadHandler m_downloadHandler;
    private volatile ConnectionHandler m_connectionHandler;
    private volatile ScheduledExecutorService m_executorService;
    private volatile AgentUpdateHandler m_agentUpdateHandler;
    private volatile LogService m_logService;
    private volatile EventAdmin m_eventAdmin;

    private final File m_workDir;

    public AgentContextImpl(File workDir) {
        m_workDir = workDir;
    }

    public void start() throws Exception {
        invokeMethod(m_configurationHandler, "start", new Class<?>[] {}, new Object[] {});
    }

    @Override
    public IdentificationHandler getIdentificationHandler() {
        return m_identificationHandler;
    }

    @Override
    public DiscoveryHandler getDiscoveryHandler() {
        return m_discoveryHandler;
    }

    @Override
    public ConnectionHandler getConnectionHandler() {
        return m_connectionHandler;
    }

    @Override
    public DeploymentHandler getDeploymentHandler() {
        return m_deploymentHandler;
    }

    @Override
    public DownloadHandler getDownloadHandler() {
        return m_downloadHandler;
    }

    @Override
    public ScheduledExecutorService getExecutorService() {
        return m_executorService;
    }

    @Override
    public ConfigurationHandler getConfigurationHandler() {
        return m_configurationHandler;
    }

    @Override
    public AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentUpdateHandler;
    }

    @Override
    public File getWorkDir() {
        return m_workDir;
    }

    @Override
    public LogService getLogService() {
        return m_logService;
    }

    @Override
    public EventAdmin getEventAdmin() {
        return m_eventAdmin;
    }

    @Override
    public AgentControl getAgentControl() {
        return m_agentControl;
    }
}
