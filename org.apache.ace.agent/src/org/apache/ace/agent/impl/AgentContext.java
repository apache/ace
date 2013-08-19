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
 * Internal interface that provides access to handlers, supporting services and static configuration.
 * 
 */
public interface AgentContext {

    /**
     * Return the identification handler.
     * 
     * @return The handler
     */
    IdentificationHandler getIdentificationHandler();

    /**
     * Return the discovery handler.
     * 
     * @return The handler
     */
    DiscoveryHandler getDiscoveryHandler();

    /**
     * Return the connection handler.
     * 
     * @return The handler
     */
    ConnectionHandler getConnectionHandler();

    /**
     * Return the deployment handler.
     * 
     * @return The handler
     */
    DeploymentHandler getDeploymentHandler();

    /**
     * Return the download handler.
     * 
     * @return The handler
     */
    DownloadHandler getDownloadHandler();

    /**
     * Return the configuration handler.
     * 
     * @return The handler
     */
    ConfigurationHandler getConfigurationHandler();

    /**
     * Return the update handler.
     * 
     * @return The handler
     */
    AgentUpdateHandler getAgentUpdateHandler();

    /**
     * Return the agent control service.
     * 
     * @return The service
     */
    AgentControl getAgentControl();

    /**
     * Return the executor service.
     * 
     * @return The service
     */
    ScheduledExecutorService getExecutorService();

    /**
     * Return the log service.
     * 
     * @return The service
     */
    LogService getLogService();

    /**
     * Return the event admin.
     * 
     * @return The service
     */
    EventAdmin getEventAdmin();

    /**
     * Return the work directory.
     * 
     * @return The directory
     */
    File getWorkDir();
}
