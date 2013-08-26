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
import java.util.Dictionary;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;

/**
 * Implementation of the internal agent context service.
 * 
 */
public class AgentContextImpl implements AgentContext {

    // All service are volatile because they may be updated at runtime.
    private volatile ConfigurationHandler m_configurationHandler;
    private volatile IdentificationHandler m_identificationHandler;
    private volatile DiscoveryHandler m_discoveryHandler;
    private volatile DeploymentHandler m_deploymentHandler;
    private volatile DownloadHandler m_downloadHandler;
    private volatile ConnectionHandler m_connectionHandler;
    private volatile AgentUpdateHandler m_agentUpdateHandler;
    private volatile FeedbackHandler m_feedbackHandler;
    private volatile LoggingHandler m_loggingHandler;
    private volatile EventsHandler m_eventsHandler;
    private volatile ScheduledExecutorService m_executorService;

    private final File m_workDir;

    public AgentContextImpl(File workDir) {
        m_workDir = workDir;
    }

    public void start() throws Exception {
        startHandler(m_executorService);
        startHandler(m_configurationHandler);
        startHandler(m_loggingHandler);
        startHandler(m_eventsHandler);
        startHandler(m_connectionHandler);
        startHandler(m_downloadHandler);
        startHandler(m_identificationHandler);
        startHandler(m_discoveryHandler);
        startHandler(m_agentUpdateHandler);
        startHandler(m_deploymentHandler);
        startHandler(m_feedbackHandler);
    }

    public void stop() throws Exception {
        stopHandler(m_feedbackHandler);
        stopHandler(m_deploymentHandler);
        stopHandler(m_identificationHandler);
        stopHandler(m_discoveryHandler);
        stopHandler(m_downloadHandler);
        stopHandler(m_connectionHandler);
        stopHandler(m_agentUpdateHandler);
        stopHandler(m_eventsHandler);
        stopHandler(m_loggingHandler);
        stopHandler(m_configurationHandler);
        stopHandler(m_executorService);
    }

    @Override
    public File getWorkDir() {
        return m_workDir;
    }

    @Override
    public IdentificationHandler getIdentificationHandler() {
        return m_identificationHandler;
    }

    void setIdentificationHandler(IdentificationHandler identificationHandler) {
        m_identificationHandler = identificationHandler;
    }

    @Override
    public DiscoveryHandler getDiscoveryHandler() {
        return m_discoveryHandler;
    }

    void setDiscoveryHandler(DiscoveryHandler discoveryHandler) {
        m_discoveryHandler = discoveryHandler;
    }

    @Override
    public ConnectionHandler getConnectionHandler() {
        return m_connectionHandler;
    }

    void setConnectionHandler(ConnectionHandler connectionHandler) {
        m_connectionHandler = connectionHandler;
    }

    @Override
    public DeploymentHandler getDeploymentHandler() {
        return m_deploymentHandler;
    }

    void setDeploymentHandler(DeploymentHandler deploymenthandler) {
        m_deploymentHandler = deploymenthandler;
    }

    @Override
    public DownloadHandler getDownloadHandler() {
        return m_downloadHandler;
    }

    void setDownloadHandler(DownloadHandler downloadHandler) {
        m_downloadHandler = downloadHandler;
    }

    @Override
    public ConfigurationHandler getConfigurationHandler() {
        return m_configurationHandler;
    }

    void setConfigurationHandler(ConfigurationHandler configurationHandler) {
        m_configurationHandler = configurationHandler;
    }

    @Override
    public AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentUpdateHandler;
    }

    void setAgentUpdateHandler(AgentUpdateHandler agentUpdateHandler) {
        m_agentUpdateHandler = agentUpdateHandler;
    }

    @Override
    public FeedbackHandler getFeedbackHandler() {
        return m_feedbackHandler;
    }

    void setFeedbackHandler(FeedbackHandler feedbackHandler) {
        m_feedbackHandler = feedbackHandler;
    }

    @Override
    public ScheduledExecutorService getExecutorService() {
        return m_executorService;
    }

    void setExecutorService(ScheduledExecutorService executorService) {
        m_executorService = executorService;
    }

    LoggingHandler getLoggingHandler() {
        return m_loggingHandler;
    }

    void setLoggingHandler(LoggingHandler loggingHandler) {
        m_loggingHandler = loggingHandler;
    }

    EventsHandler getEventHandler() {
        return m_eventsHandler;
    }

    void setEventsHandler(EventsHandler eventsHandler) {
        m_eventsHandler = eventsHandler;
    }

    @Override
    public void postEvent(String topic, Dictionary<String, String> payload) {
        m_eventsHandler.postEvent(topic, payload);
    }

    @Override
    public void logDebug(String component, String message, Object... args) {
        m_loggingHandler.logDebug(component, message, null, args);
    }

    @Override
    public void logDebug(String component, String message, Throwable exception, Object... args) {
        m_loggingHandler.logDebug(component, message, exception, args);
    }

    @Override
    public void logInfo(String component, String message, Object... args) {
        m_loggingHandler.logInfo(component, message, null, args);
    }

    @Override
    public void logInfo(String component, String message, Throwable exception, Object... args) {
        m_loggingHandler.logInfo(component, message, exception, args);
    }

    @Override
    public void logWarning(String component, String message, Object... args) {
        m_loggingHandler.logWarning(component, message, null, args);
    }

    @Override
    public void logWarning(String component, String message, Throwable exception, Object... args) {
        m_loggingHandler.logWarning(component, message, exception, args);
    }

    @Override
    public void logError(String component, String message, Object... args) {
        m_loggingHandler.logDebug(component, message, null, args);
    }

    @Override
    public void logError(String component, String message, Throwable exception, Object... args) {
        m_loggingHandler.logDebug(component, message, exception, args);
    }

    private void startHandler(Object handler) throws Exception {
        if (handler instanceof AgentContextAware) {
            ((AgentContextAware) handler).start(this);
        }
    }

    private void stopHandler(Object handler) throws Exception {
        if (handler instanceof AgentContextAware) {
            ((AgentContextAware) handler).stop();
        }
    }
}
