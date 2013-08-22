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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;

/**
 * Default configurable controller
 * 
 */
public class DefaultController implements Runnable, AgentContextAware {

    public static final String COMPONENT_IDENTIFIER = "controller";
    public static final String CONFIG_KEY_BASE = ConfigurationHandlerImpl.CONFIG_KEY_NAMESPACE + ".controller";

    /**
     */
    public static final String CONFIG_KEY_DISABLED = CONFIG_KEY_BASE + ".disabled";
    public static final boolean CONFIG_DEFAULT_DISABLED = false;

    /**
     * Sync delay; Number of seconds after startup until the initial sync is done.
     */
    public static final String CONFIG_KEY_SYNCDELAY = CONFIG_KEY_BASE + ".syncDelay";
    public static final long CONFIG_DEFAULT_SYNCDELAY = 5l;

    /**
     * Sync interval; Number of seconds between regular syncs.
     */
    public static final String CONFIG_KEY_SYNCINTERVAL = CONFIG_KEY_BASE + ".syncInterval";
    public static final long CONFIG_DEFAULT_SYNCINTERVAL = 30l;

    /**
     * SyncRetries value; When an install fails during a sync the agent can try to recover by ignoring optimization
     * flags and potentially restarting a Deployment Package download. A value of 1 or less disables the retry behavior.
     */
    public static final String CONFIG_KEY_UPDATERETRIES = CONFIG_KEY_BASE + ".updateRetries";
    public static final long CONFIG_DEFAULT_UPDATERETRIES = 1;

    /**
     * UpdateStreaming flag; When set Deployment Packages are installed directly from the download stream reducing
     * overhead and disk usage, but disabling resume capabilities. This strategy is of interest to highly resource
     * constraint devices and/or system with highly reliable connectivity and no need for resume semantics.
     */
    public static final String CONFIG_KEY_UPDATESTREAMING = CONFIG_KEY_BASE + ".updateStreaming";
    public static final boolean CONFIG_DEFAULT_UPDATESTREAMING = false;

    /**
     * StopUnaffected flag; When set all target bundles of a Deployment Package will be restarted as part of the
     * deployment session. Otherwise the agent tries to minimize the impact by only restarting bundles that are actually
     * affected. Not stopping unaffected bundles reduces overhead, but may fail in complex wiring scenarios.
     */
    public static final String CONFIG_KEY_STOPUNAFFECTED = CONFIG_KEY_BASE + ".stopUnaffected";
    public static final boolean CONFIG_DEFAULT_STOPUNAFFECTED = true; // spec behavior

    /**
     * FixPackages flag; When set the Agent will request the server for fix packages instead of full deployment
     * packages. This behavior significantly reduces bandwidth consumption.
     */
    public static final String CONFIG_KEY_FIXPACKAGES = CONFIG_KEY_BASE + ".fixPackages";
    public static final boolean CONFIG_DEFAULT_FIXPACKAGES = true;

    private volatile AgentContext m_agentContext;
    private volatile ScheduledFuture<?> m_future;

    @Override
    public void start(AgentContext agentContext) throws Exception {
        m_agentContext = agentContext;

        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();

        boolean disabled = configurationHandler.getBoolean(CONFIG_KEY_DISABLED, CONFIG_DEFAULT_DISABLED);
        if (disabled) {
            m_agentContext.logInfo(COMPONENT_IDENTIFIER, "Default controller disabled by configuration");
        }
        else {
            long delay = configurationHandler.getLong(CONFIG_KEY_SYNCDELAY, CONFIG_DEFAULT_SYNCDELAY);
            scheduleRun(delay);
            m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Controller scheduled to sync in %d seconds", delay);
        }
    }

    public void stop() {
        unscheduleRun();
    }

    public void run() {
        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Controller syncing...");
        long interval = configurationHandler.getLong(CONFIG_KEY_SYNCINTERVAL, CONFIG_DEFAULT_SYNCINTERVAL);
        try {
            runSafeFeedback();
            runSafeAgent();
            runSafeUpdate();
        }
        catch (RetryAfterException e) {
            interval = e.getSeconds();
            m_agentContext.logInfo(COMPONENT_IDENTIFIER, "Sync received retry exception from server.");
        }
        catch (IOException e) {
            m_agentContext.logWarning(COMPONENT_IDENTIFIER, "Sync aborted due to IOException.", e);
        }
        catch (Exception e) {
            m_agentContext.logError(COMPONENT_IDENTIFIER, "Sync aborted due to Exception.", e);
        }
        scheduleRun(interval);
        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Sync completed. Rescheduled in %d seconds", interval);
    }

    private void runSafeAgent() throws RetryAfterException, IOException {
        AgentUpdateHandler deploymentHandler = m_agentContext.getAgentUpdateHandler();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Checking for agent update");
        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        if (highest.compareTo(current) > 0) {
            m_agentContext.logInfo(COMPONENT_IDENTIFIER, "Installing agent update %s => %s", current, highest);
            InputStream inputStream = deploymentHandler.getInputStream(highest);
            deploymentHandler.install(inputStream);
        }
        else {
            m_agentContext.logDebug(COMPONENT_IDENTIFIER, "No agent update available for version %s", current);
        }
    }

    private void runSafeUpdate() throws RetryAfterException, IOException {
        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();
        DeploymentHandler deploymentHandler = m_agentContext.getDeploymentHandler();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Checking for deployment update");
        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        if (highest.compareTo(current) > 0) {
            m_agentContext.logInfo(COMPONENT_IDENTIFIER, "Installing deployment update %s => %s", current, highest);

            // FIXME handle downloads
            // boolean streaming = configurationHandler.getBoolean(CONFIG_KEY_STREAMING_UPDATES,
            // CONFIG_DEFAULT_UPDATE_STREAMING);
            boolean fixPackage = configurationHandler.getBoolean(CONFIG_KEY_FIXPACKAGES, CONFIG_DEFAULT_FIXPACKAGES);
            InputStream inputStream = deploymentHandler.getInputStream(highest, fixPackage);
            try {
                deploymentHandler.deployPackage(inputStream);
            }
            finally {
                inputStream.close();
            }
        }
        else {
            m_agentContext.logDebug(COMPONENT_IDENTIFIER, "No deployment update available for version %s", current);
        }
    }

    private void runSafeFeedback() throws RetryAfterException, IOException {
        AgentControl agentControl = m_agentContext.getAgentControl();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Synchronizing feedback channels");
        List<String> channelNames = agentControl.getFeedbackHandler().getChannelNames();
        for (String channelName : channelNames) {
            FeedbackChannel channel = agentControl.getFeedbackHandler().getChannel(channelName);
            if (channel != null) {
                channel.sendFeedback();
            }
        }
    }

    private void scheduleRun(long seconds) {
        m_future = m_agentContext.getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void unscheduleRun() {
        if (m_future != null)
            m_future.cancel(true);
    }
}
