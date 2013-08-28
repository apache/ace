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

import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_DISABLED;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_FIXPACKAGES;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_RETRIES;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_STREAMING;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_SYNCDELAY;
import static org.apache.ace.agent.AgentConstants.CONFIG_CONTROLLER_SYNCINTERVAL;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentException;

/**
 * Default configurable controller
 */
public class DefaultController extends ComponentBase implements Runnable {

    private volatile ScheduledFuture<?> m_scheduledFuture;
    private volatile UpdateInstaller m_updateInstaller;

    public DefaultController() {
        super("controller");
    }

    @Override
    protected void onStart() throws Exception {
        long delay = getConfigurationHandler().getLong(CONFIG_CONTROLLER_SYNCDELAY, 5);
        scheduleRun(delay);
        logDebug("Controller scheduled to run in %d seconds", delay);
    }

    @Override
    protected void onStop() throws Exception {
        if (m_updateInstaller != null) {
            m_updateInstaller.reset();
        }
        unscheduleRun();
    }

    @Override
    public void run() {
        boolean disabled = getConfigurationHandler().getBoolean(CONFIG_CONTROLLER_DISABLED, false);
        long interval = getConfigurationHandler().getLong(CONFIG_CONTROLLER_SYNCINTERVAL, 60);
        if (disabled) {
            logDebug("Controller disabled by configuration. Skipping..");
            scheduleRun(interval);
            return;
        }

        logDebug("Controller syncing...");
        try {
            runFeedback();
            runAgentUpdate();
            runDeploymentUpdate();
        }
        catch (RetryAfterException e) {
            // any method may throw this causing the sync to abort. The server is busy so no sense in trying
            // anything else until the retry window has passed.
            interval = e.getSeconds();
            logWarning("Sync received retry exception from server. Rescheduled in %d seconds", e.getSeconds());
        }
        catch (Exception e) {
            // serious problem throw by a method that decides this is cause enough to abort the sync. Not much
            // we can do but log it as an error and reschedule as usual.
            logError("Sync aborted due to Exception.", e);
        }
        scheduleRun(interval);
        logDebug("Sync completed. Rescheduled in %d seconds", interval);
    }

    private void runFeedback() throws RetryAfterException {
        logDebug("Synchronizing feedback channels");
        Set<String> names = getFeedbackChannelNames();
        for (String name : names) {
            FeedbackChannel channel = getFeedbackChannel(name);
            if (channel != null) {
                try {
                    channel.sendFeedback();
                    logDebug("Feedback send succesfully for channel: %s", name);
                }
                catch (IOException e) {
                    // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we
                    // just log it as a warning.
                    logWarning("Exception while sending feedback on channel: %s", e, name);
                }
            }
        }
    }

    private Set<String> getFeedbackChannelNames() {
        try {
            return getFeedbackHandler().getChannelNames();
        }
        catch (IOException e) {
            // Probably a serious problem due to local IO related to feedback. No cause to abort the sync so we just log
            // it as an error.
            logError("Exception while Looking up feedback channelnames. This is ");
        }
        return Collections.emptySet();
    }

    private FeedbackChannel getFeedbackChannel(String name) {
        try {
            return getFeedbackHandler().getChannel(name);
        }
        catch (IOException e) {
            // Probably a serious problem due to local IO related to feedback. No cause to abort the sync so we just log
            // it as an error.
            logError("Exception while looking up feedback channel %s", e, name);
        }
        return null;
    }

    private void runAgentUpdate() throws RetryAfterException {
        logDebug("Checking for agent update");
        Version current = getAgentUpdateHandler().getInstalledVersion();
        SortedSet<Version> available = getAvailableAgentVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }

        if (highest.compareTo(current) < 1) {
            logDebug("No agent update available for version %s", current);
            return;
        }

        logInfo("Installing agent update %s => %s", current, highest);
        InputStream inputStream = null;
        try {
            inputStream = getAgentUpdateHandler().getInputStream(highest);
            getAgentUpdateHandler().install(inputStream);
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we
            // just log it as a warning.
            // FIXME Does not cover failed updates and should handle retries
            logWarning("Exception while installing agent update %s", e, highest);
        }
    }

    private SortedSet<Version> getAvailableAgentVersions() throws RetryAfterException {
        try {
            return getAgentUpdateHandler().getAvailableVersions();
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we just
            // log it as a warning.
            logWarning("Exception while retrieving agent versions", e);
        }
        return new TreeSet<Version>();
    }

    private void runDeploymentUpdate() throws RetryAfterException {

        logDebug("Checking for deployment update");
        Version current = getDeploymentHandler().getInstalledVersion();
        SortedSet<Version> available = getAvailableDeploymentVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }

        if (highest.compareTo(current) < 1) {
            logDebug("No deployment update available for version %s", current);
            return;
        }

        boolean updateStreaming = getConfigurationHandler().getBoolean(CONFIG_CONTROLLER_STREAMING, true);
        boolean fixPackage = getConfigurationHandler().getBoolean(CONFIG_CONTROLLER_FIXPACKAGES, true);
        long maxRetries = getConfigurationHandler().getLong(CONFIG_CONTROLLER_RETRIES, 1);

        getUpdateInstaller(updateStreaming).installUpdate(current, highest, fixPackage, maxRetries);
    }

    private SortedSet<Version> getAvailableDeploymentVersions() throws RetryAfterException {
        try {
            return getDeploymentHandler().getAvailableVersions();
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we just
            // log it as a warning.
            logWarning("Exception while retrieving deployment versions", e);
        }
        return new TreeSet<Version>();
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
        m_scheduledFuture = getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void unscheduleRun() {
        if (m_scheduledFuture != null)
            m_scheduledFuture.cancel(true);
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
                    getController().logInfo("Ignoring deployment update %s => %s because max retries reached %d", fromVersion, toVersion, maxRetries);
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
            catch (DeploymentException e) {
                getController().logWarning("Exception while deploying the package", e);
                e.printStackTrace();
                m_failureCount++;
            }
            catch (IOException e) {
                getController().logWarning("Exception opening/streaming package inputstream", e);
                e.printStackTrace();
                m_failureCount++;
            }
        }

        public final void reset() {
            m_lastVersion = null;
            m_failureCount = 0;
            doReset();
        }

        protected abstract void doInstallUpdate(Version from, Version to, boolean fix) throws RetryAfterException, DeploymentException, IOException;

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
        public void doInstallUpdate(Version from, Version to, boolean fix) throws RetryAfterException, DeploymentException, IOException {

            getController().logInfo("Installing streaming deployment update %s => %s", from, to);

            DeploymentHandler deploymentHandler = getController().getDeploymentHandler();
            InputStream inputStream = null;
            try {
                inputStream = deploymentHandler.getInputStream(to, fix);
                deploymentHandler.deployPackage(inputStream);
                return;
            }
            finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    }
                    catch (Exception e) {
                        getController().logWarning("Exception while closing streaming package inputstream", e);
                    }
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
        public void doInstallUpdate(Version fromVersion, Version toVersion, boolean fixPackage) throws RetryAfterException, DeploymentException, IOException {

            DeploymentHandler deploymentHandler = getController().getDeploymentHandler();
            if (m_downloadHandle != null && !m_downloadVersion.equals(toVersion)) {
                getController().logInfo("Cancelling deployment package download for %s because a newer version is available", m_downloadVersion);
                m_downloadHandle.discard();
                m_downloadHandle = null;
            }

            if (m_downloadHandle == null) {
                getController().logInfo("Starting deployment package download %s => %s", fromVersion, toVersion);
                m_downloadVersion = toVersion;
                m_downloadHandle = deploymentHandler.getDownloadHandle(toVersion, fixPackage)
                    .setProgressListener(this).setCompletionListener(this).start();
            }
            else {
                if (m_downloadResult == null) {
                    getController().logInfo("Deployment package download for %s is in progress %d / %d", toVersion, m_downloadProgress, m_downloadLength);
                }
                else if (m_downloadResult.getState() == DownloadState.FAILED) {
                    getController().logWarning("Deployment package download for %s is FAILED. Clearing for retry");
                    m_downloadHandle.discard();
                    m_downloadHandle = null;
                    throw new IOException("Download failed");
                }
                else if (m_downloadResult.getState() == DownloadState.STOPPED) {
                    getController().logWarning("Deployment package download for %s is STOPPED. Trying to resume");
                    m_downloadResult = null;
                    m_downloadHandle.start();
                }
                else if (m_downloadResult.getState() == DownloadState.SUCCESSFUL) {
                    getController().logInfo("Installing downloaded deployment update %s => %s", fromVersion, toVersion);
                    InputStream inputStream = new FileInputStream(m_downloadResult.getFile());
                    System.out.println(m_downloadResult.getFile().getAbsolutePath());
                    try {
                        deploymentHandler.deployPackage(inputStream);
                    }
                    finally {
                        // m_downloadHandle.discard();
                        m_downloadHandle = null;
                        inputStream.close();
                    }
                }
            }
        }

        @Override
        public void doReset() {
            if (m_downloadHandle != null) {
                getController().logInfo("Cancelling deployment package download for version %s because of reset", m_downloadVersion);
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
            getController().logInfo("Deployment package donwload completed for version %s. Rescheduling the controller to run in %d seconds", m_downloadVersion, 1);
            getController().scheduleRun(1);
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
