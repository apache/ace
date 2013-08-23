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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;
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
    public static final long CONFIG_DEFAULT_UPDATERETRIES = 2;

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
    private volatile ScheduledFuture<?> m_scheduledFuture;
    private volatile UpdateInstaller m_updateInstaller;

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
        if (m_updateInstaller != null) {
            m_updateInstaller.reset();
        }
    }

    public void run() {
        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Controller syncing...");
        long interval = configurationHandler.getLong(CONFIG_KEY_SYNCINTERVAL, CONFIG_DEFAULT_SYNCINTERVAL);
        try {
            runSafeFeedback();
            runSafeAgentUpdate();
            runSafeDeploymentUpdate();
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

    private void runSafeFeedback() throws RetryAfterException, IOException {
        AgentControl agentControl = m_agentContext.getAgentControl();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Synchronizing feedback channels");
        Set<String> channelNames = agentControl.getFeedbackHandler().getChannelNames();
        for (String channelName : channelNames) {
            FeedbackChannel channel = agentControl.getFeedbackHandler().getChannel(channelName);
            if (channel != null) {
                channel.sendFeedback();
            }
        }
    }

    private void runSafeAgentUpdate() throws RetryAfterException, IOException {
        AgentUpdateHandler updateHandler = m_agentContext.getAgentUpdateHandler();

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Checking for agent update");
        Version current = updateHandler.getInstalledVersion();
        SortedSet<Version> available = updateHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        if (highest.compareTo(current) > 0) {
            m_agentContext.logInfo(COMPONENT_IDENTIFIER, "Installing agent update %s => %s", current, highest);
            InputStream inputStream = updateHandler.getInputStream(highest);
            updateHandler.install(inputStream);
        }
        else {
            m_agentContext.logDebug(COMPONENT_IDENTIFIER, "No agent update available for version %s", current);
        }
    }

    private void runSafeDeploymentUpdate() throws RetryAfterException, IOException {

        m_agentContext.logDebug(COMPONENT_IDENTIFIER, "Checking for deployment update");
        DeploymentHandler deploymentHandler = m_agentContext.getDeploymentHandler();
        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        if (highest.compareTo(current) < 1) {
            m_agentContext.logDebug(COMPONENT_IDENTIFIER, "No deployment update available for version %s", current);
            return;
        }

        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();
        boolean fixPackage = configurationHandler.getBoolean(CONFIG_KEY_FIXPACKAGES, CONFIG_DEFAULT_FIXPACKAGES);
        boolean updateStreaming = configurationHandler.getBoolean(CONFIG_KEY_UPDATESTREAMING, CONFIG_DEFAULT_UPDATESTREAMING);
        long maxRetries = configurationHandler.getLong(CONFIG_KEY_UPDATERETRIES, CONFIG_DEFAULT_UPDATERETRIES);

        getUpdateInstaller(updateStreaming).installUpdate(current, highest, fixPackage, maxRetries);
    }

    private UpdateInstaller getUpdateInstaller(boolean streaming) {
        if (streaming) {
            if (m_updateInstaller == null) {
                m_updateInstaller = new StreamingUpdateInstaller(this);
            }
            else if (!(m_updateInstaller instanceof StreamingUpdateInstaller)) {
                m_updateInstaller.reset();
                m_updateInstaller = new StreamingUpdateInstaller(this);
            }
        }
        else {
            if (m_updateInstaller == null) {
                m_updateInstaller = new DownloadUpdateInstaller(this);
            }
            if (!(m_updateInstaller instanceof DownloadUpdateInstaller)) {
                m_updateInstaller.reset();
                m_updateInstaller = new DownloadUpdateInstaller(this);
            }
        }
        return m_updateInstaller;
    }

    private void scheduleRun(long seconds) {
        unscheduleRun();
        m_scheduledFuture = m_agentContext.getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void unscheduleRun() {
        if (m_scheduledFuture != null)
            m_scheduledFuture.cancel(true);
    }

    private AgentContext getAgentContext() {
        return m_agentContext;
    }

    /**
     * Base class for internal installer strategies. This implementation handles max update retry contraints and
     * delegates the rest to concrete implementations.
     */
    abstract static class UpdateInstaller {

        private final DefaultController m_controller;
        private Version m_lastVersion = null;
        private int m_failureCount = 0;

        public UpdateInstaller(DefaultController controller) {
            m_controller = controller;
        }

        protected final DefaultController getController() {
            return m_controller;
        }

        public final void installUpdate(Version fromVersion, Version toVersion, boolean fixPackage, long maxRetries) throws RetryAfterException {
            if (m_lastVersion != null && toVersion.equals(m_lastVersion)) {
                if (m_failureCount >= maxRetries) {
                    getController().getAgentContext().logInfo(COMPONENT_IDENTIFIER,
                        "Ignoring deployment update %s => %s because max retries reached %d", fromVersion, toVersion, maxRetries);
                    return;
                }
            }
            else {
                m_lastVersion = toVersion;
                m_failureCount = 0;
            }
            try {
                doInstallUpdate(fromVersion, toVersion, fixPackage);
            }
            catch (RetryAfterException e) {
                // The server is busy. Re-throw so the controller can abort the sync and reschedule.
                throw (e);

            }
            catch (IOException e) {
                // Just increment the failure count and asume the concrete implementation logged.
                m_failureCount++;
            }
        }

        public final void reset() {
            m_lastVersion = null;
            m_failureCount = 0;
            doReset();
        }

        protected abstract void doInstallUpdate(Version from, Version to, boolean fix) throws RetryAfterException, IOException;

        protected abstract void doReset();
    }

    /**
     * UpdateInstaller that provides streaming deployment package install. The install is blocking.
     */
    static class StreamingUpdateInstaller extends UpdateInstaller {

        public StreamingUpdateInstaller(DefaultController controller) {
            super(controller);
        }

        @Override
        public void doInstallUpdate(Version from, Version to, boolean fix) throws RetryAfterException, IOException {

            getController().getAgentContext().logInfo(COMPONENT_IDENTIFIER,
                "Installing streaming deployment update %s => %s", from, to);

            DeploymentHandler deploymentHandler = getController().getAgentContext().getDeploymentHandler();
            InputStream inputStream = null;
            try {
                inputStream = deploymentHandler.getInputStream(to, fix);
                deploymentHandler.deployPackage(inputStream);
                return;
            }
            catch (IOException e) {
                getController().getAgentContext().logWarning(COMPONENT_IDENTIFIER,
                    "IOException opening/streaming package inputstream", e);
                throw e;
            }
            finally {
                if (inputStream != null)
                    try {
                        inputStream.close();
                    }
                    catch (Exception e) {
                        getController().getAgentContext().logWarning(COMPONENT_IDENTIFIER,
                            "Exception while closing streaming package inputstream", e);
                    }
            }
        }

        @Override
        protected void doReset() {
        }
    }

    /**
     * UpdateInstaller that provides download deployment package install. The install is non-blocking. Upon download
     * completion this installer will reschedule the controller.
     */
    static class DownloadUpdateInstaller extends UpdateInstaller implements DownloadHandle.ProgressListener, DownloadHandle.ResultListener {

        // active download state
        private volatile DownloadHandle m_downloadHandle;
        private volatile DownloadResult m_downloadResult = null;
        private volatile Version m_downloadVersion;
        private volatile long m_downloadLength = 0;
        private volatile long m_downloadProgress = 0;

        public DownloadUpdateInstaller(DefaultController controller) {
            super(controller);
        }

        @Override
        public void doInstallUpdate(Version fromVersion, Version toVersion, boolean fixPackage) throws RetryAfterException, IOException {

            AgentContext agentContext = getController().getAgentContext();
            DeploymentHandler deploymentHandler = agentContext.getDeploymentHandler();

            if (m_downloadHandle != null && !m_downloadVersion.equals(toVersion)) {

                agentContext.logInfo(COMPONENT_IDENTIFIER,
                    "Cancelling deployment package download for %s because a newer version is available", m_downloadVersion);
                m_downloadHandle.discard();
                m_downloadHandle = null;
            }

            if (m_downloadHandle == null) {

                agentContext.logInfo(COMPONENT_IDENTIFIER, "Starting deployment package download %s => %s", fromVersion, toVersion);
                m_downloadVersion = toVersion;
                m_downloadHandle = agentContext.getDeploymentHandler().getDownloadHandle(toVersion, fixPackage)
                    .setProgressListener(this).setCompletionListener(this).start();
            }
            else {

                if (m_downloadResult == null) {
                    agentContext.logInfo(COMPONENT_IDENTIFIER,
                        "Deployment package download for %s is in progress %d / %d", toVersion, m_downloadProgress, m_downloadLength);
                }
                else if (m_downloadResult.getState() == DownloadState.FAILED) {
                    agentContext.logWarning(COMPONENT_IDENTIFIER,
                        "Deployment package download for %s is FAILED. Clearing for retry");
                    m_downloadHandle.discard();
                    m_downloadHandle = null;
                    throw new IOException("Download failed");
                }
                else if (m_downloadResult.getState() == DownloadState.STOPPED) {
                    agentContext.logWarning(COMPONENT_IDENTIFIER,
                        "Deployment package download for %s is STOPPED. Trying to resume");
                    m_downloadResult = null;
                    m_downloadHandle.start();
                }
                else if (m_downloadResult.getState() == DownloadState.SUCCESSFUL) {

                    agentContext.logInfo(COMPONENT_IDENTIFIER,
                        "Installing downloaded deployment update %s => %s", fromVersion, toVersion);

                    InputStream inputStream = new FileInputStream(m_downloadResult.getFile());
                    try {
                        deploymentHandler.deployPackage(inputStream);
                    }
                    finally {
                        m_downloadHandle.discard();
                        m_downloadHandle = null;
                        inputStream.close();
                    }
                }
            }
        }

        @Override
        public void doReset() {
            if (m_downloadHandle != null) {
                getController().getAgentContext().logInfo(COMPONENT_IDENTIFIER,
                    "Cancelling deployment package download for version %s because of reset", m_downloadVersion);
                m_downloadHandle.discard();
            }
            clearDownloadState();
        }

        @Override
        public void progress(long contentLength, long progress) {
            m_downloadLength = contentLength;
            m_downloadProgress = progress;
        }

        @Override
        public void completed(DownloadResult result) {
            m_downloadResult = result;
            getController().getAgentContext().logInfo(COMPONENT_IDENTIFIER,
                "Deployment package completed for version %s. Rescheduling the controller to run in %d seconds", m_downloadVersion, 5);
            getController().scheduleRun(5);
        }

        private void clearDownloadState() {
            if (m_downloadHandle != null) {
                m_downloadHandle.discard();
            }
            m_downloadHandle = null;
            m_downloadResult = null;
            m_downloadVersion = null;
        }
    }
}
