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
import static org.apache.ace.agent.AgentConstants.EVENT_AGENT_CONFIG_CHANGED;
import static org.apache.ace.agent.impl.ConnectionUtil.closeSilently;
import static org.apache.ace.agent.impl.InternalConstants.AGENT_INSTALLATION_COMPLETE;
import static org.apache.ace.agent.impl.InternalConstants.AGENT_INSTALLATION_START;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandle.DownloadProgressListener;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.InstallationFailedException;
import org.apache.ace.agent.RetryAfterException;
import org.apache.ace.agent.UpdateHandler;
import org.osgi.framework.Version;

/**
 * Default configurable controller
 */
public class DefaultController extends ComponentBase implements Runnable, EventListener {
    /**
     * UpdateInstaller that provides download deployment package install. The install is non-blocking. Upon download
     * completion this installer will reschedule the controller.
     */
    static class DownloadUpdateInstaller extends UpdateInstaller implements DownloadProgressListener {
        private volatile DownloadHandle m_downloadHandle;
        private volatile UpdateInfo m_updateInfo;
        private volatile Future<DownloadResult> m_future;

        public DownloadUpdateInstaller(DefaultController controller) {
            super(controller);
        }

        @Override
        public void doInstallUpdate(final UpdateHandler delegate, final UpdateInfo updateInfo) throws RetryAfterException {
            DefaultController controller = getController();

            String type = updateInfo.m_type;
            Version fromVersion = updateInfo.m_from;
            Version toVersion = updateInfo.m_to;

            if (m_downloadHandle != null) {
                // Ongoing download?
                if (m_updateInfo != null && !m_updateInfo.m_to.equals(toVersion)) {
                    controller.logInfo("Cancelling download of %s update for %s because a newer version is available...", m_updateInfo.m_type, m_updateInfo.m_to);

                    clearDownloadState();
                }
            }

            if (m_downloadHandle == null) {
                controller.logInfo("Starting download of %s update, %s => %s...", updateInfo.m_type, updateInfo.m_from, updateInfo.m_to);

                m_updateInfo = updateInfo;
                m_future = null;

                m_downloadHandle = delegate.getDownloadHandle(updateInfo.m_to, updateInfo.m_fixPackage);
            }

            if (m_future == null) {
                m_future = m_downloadHandle.start(this);
            }
            else {
                if (!m_future.isDone()) {
                    controller.logDebug("Still awaiting completion of download...");
                    return;
                }
                else if (m_future.isCancelled()) {
                    controller.logInfo("Download of %s update is CANCELLED. Resuming download...", type);

                    // We're stopped early...
                    m_future = m_downloadHandle.start(this);
                    return;
                }

                try {
                    try {
                        DownloadResult downloadResult = m_future.get();

                        if (downloadResult.isComplete()) {
                            controller.logInfo("Installing %s update %s => %s...", type, fromVersion, toVersion);

                            startInstallation(updateInfo);

                            delegate.install(downloadResult.getInputStream());

                            endInstallation(updateInfo, true /* success */, null);

                            clearDownloadState();
                        }
                        else {
                            controller.logInfo("Download of %s update is STOPPED. Resuming download...", type);

                            // We're stopped early...
                            m_future = m_downloadHandle.start(this);
                        }
                    }
                    catch (InterruptedException exception) {
                        controller.logInfo("Download of %s update is INTERRUPTED. Resuming download...", type);

                        // We're stopped early...
                        m_future = m_downloadHandle.start(this);
                    }
                    catch (ExecutionException exception) {
                        clearDownloadState();

                        Throwable cause = exception.getCause();
                        if (cause instanceof RetryAfterException) {
                            throw (RetryAfterException) cause;
                        }
                        else if (cause instanceof Exception) {
                            throw (Exception) cause;
                        }
                        else {
                            throw new RuntimeException("Failed to handle cause!", cause);
                        }
                    }
                }
                catch (RetryAfterException ex) {
                    // Does not cause the installation to end...
                    throw ex;
                }
                catch (Exception ex) {
                    // All other exceptions cause the installation to end/fail...
                    endInstallation(updateInfo, false /* success */, ex);
                }
            }
        }

        @Override
        public void progress(long bytesRead, long totalBytes) {
            if (m_updateInfo != null) {
                getController().logInfo("Progress of %s update download: %d of %d bytes...", m_updateInfo.m_type, bytesRead, totalBytes);
            }
        }

        @Override
        public void doReset() {
            if (m_downloadHandle != null) {
                getController().logInfo("Cancelling deployment package download for version %s because of reset...", m_updateInfo.m_to);
                m_downloadHandle.discard();
            }
            clearDownloadState();
        }

        private void clearDownloadState() {
            if (m_downloadHandle != null) {
                m_downloadHandle.discard();
            }
            m_downloadHandle = null;
            if (m_future != null && !m_future.isDone()) {
                m_future.cancel(true /* mayInterruptIfRunning */);
            }
            m_future = null;
            m_updateInfo = null;
        }
    }

    /**
     * UpdateInstaller that provides streaming deployment package install. The install is blocking.
     */
    static class StreamingUpdateInstaller extends UpdateInstaller {
        public StreamingUpdateInstaller(DefaultController controller) {
            super(controller);
        }

        @Override
        public void doInstallUpdate(UpdateHandler delegate, UpdateInfo updateInfo) throws RetryAfterException {
            DefaultController controller = getController();

            controller.logInfo("Installing streaming %s update %s => %s", updateInfo.m_type, updateInfo.m_from, updateInfo.m_to);

            InputStream inputStream = null;

            try {
                inputStream = delegate.getInputStream(updateInfo.m_to, updateInfo.m_fixPackage);

                startInstallation(updateInfo);

                delegate.install(inputStream);

                endInstallation(updateInfo, true /* success */, null);
            }
            catch (RetryAfterException ex) {
                // We aren't ready yet...
                throw ex;
            }
            catch (Exception ex) {
                endInstallation(updateInfo, false /* success */, ex);
            }
            finally {
                closeSilently(inputStream);
            }
        }

        @Override
        protected void doReset() {
            // Nop
        }
    }

    /**
     * Small container for information about an update.
     */
    static class UpdateInfo {
        final Version m_from;
        final Version m_to;
        final boolean m_fixPackage;
        final String m_type;

        public UpdateInfo(String type, Version from, Version to, boolean fixPackage) {
            m_from = from;
            m_to = to;
            m_type = type;
            m_fixPackage = fixPackage;
        }
    }

    /**
     * Base class for internal installer strategies. This implementation handles max update retry constraints and
     * delegates the rest to concrete implementations.
     */
    static abstract class UpdateInstaller {
        private final DefaultController m_controller;
        private Version m_lastVersionTried = null;
        private boolean m_lastVersionSuccessful = true;
        private int m_failureCount = 0;

        public UpdateInstaller(DefaultController controller) {
            m_controller = controller;
        }

        /**
         * Checks whether there's an update to install, and if so, uses the given delegate to actually install the
         * update.
         * 
         * @param delegate
         *            the update handle to use for installing the update;
         * @param fixPackage
         *            <code>true</code> if the update should be downloaded as a "fix package", or <code>false</code> if
         *            it should be a "complete" update;
         * @param maxRetries
         *            the maximum number of times an update should be retries.
         * @throws RetryAfterException
         *             in case the server is too busy and we should defer our update to a later moment in time;
         * @throws IOException
         *             in case of problems accessing the server.
         */
        public final void installUpdate(UpdateHandler delegate, boolean fixPackage, long maxRetries) throws RetryAfterException, IOException {
            Version fromVersion = delegate.getInstalledVersion();
            Version toVersion = delegate.getHighestAvailableVersion();

            UpdateInfo updateInfo = new UpdateInfo(delegate.getName(), fromVersion, toVersion, fixPackage);

            // Check whether we actually do need to do something...
            if (!canInstallUpdate(updateInfo, maxRetries)) {
                return;
            }

            if (m_lastVersionTried == null || !toVersion.equals(m_lastVersionTried)) {
                m_lastVersionTried = toVersion;
                m_lastVersionSuccessful = true;
                m_failureCount = 0;
            }

            doInstallUpdate(delegate, updateInfo);
        }

        /**
         * Called when we should discard any pending installations.
         */
        public final void reset() {
            m_lastVersionTried = null;
            m_failureCount = 0;
            doReset();
        }

        /**
         * Called when an update is available and should be installed. Implementations should do the actual installation
         * of the update using the given delegate.
         * 
         * @param delegate
         *            the delegate to use for installing the update;
         * @param updateInfo
         *            some information about the update.
         * @throws RetryAfterException
         *             in case the server is too busy and we should defer our update to a later moment in time.
         */
        protected abstract void doInstallUpdate(UpdateHandler delegate, UpdateInfo updateInfo) throws RetryAfterException;

        /**
         * Called when we should discard any pending installations.
         */
        protected abstract void doReset();

        /**
         * Should be called to notify that an installation is ended, successfully or unsuccessfully.
         * 
         * @param updateInfo
         *            the information about the update;
         * @param success
         *            <code>true</code> if the installation was successful, <code>false</code> otherwise;
         * @param cause
         *            the (optional) cause why the installation failed.
         */
        protected final void endInstallation(UpdateInfo updateInfo, boolean success, Exception cause) throws RetryAfterException {
            m_lastVersionSuccessful = success;
            if (cause instanceof InstallationFailedException || cause instanceof IOException) {
                m_failureCount++;
            }
            else {
                m_failureCount = 0;
            }
            m_controller.sendDeploymentCompletedEvent(updateInfo, success);
        }

        protected final DefaultController getController() {
            return m_controller;
        }

        /**
         * Should be called to notify that an installation is started.
         * 
         * @param updateInfo
         *            the information about the update.
         */
        protected final void startInstallation(UpdateInfo updateInfo) {
            m_controller.sendDeploymentInstallEvent(updateInfo);
        }

        /**
         * Determines whether, according to the given information about the possible update, we should actually perform
         * an update or not.
         * 
         * @param updateInfo
         *            the information about the possible update;
         * @param maxRetries
         *            the maximum number of times an installation should be retried.
         * @return <code>true</code> if there is a possible update to install, <code>false</code> otherwise.
         */
        private boolean canInstallUpdate(UpdateInfo updateInfo, long maxRetries) {
            Version fromVersion = updateInfo.m_from;
            Version toVersion = updateInfo.m_to;
            String type = updateInfo.m_type;

            if (toVersion.compareTo(fromVersion) > 0) {
                // Possible newer version, lets check our administration whether we actually need to do something...
                if (m_lastVersionTried != null && toVersion.equals(m_lastVersionTried)) {
                    if (m_failureCount >= maxRetries) {
                        m_controller.logDebug("Ignoring %s update %s => %s because max retries (%d) reached!", type, fromVersion, toVersion, maxRetries);
                        return false;
                    }
                    if (!m_lastVersionSuccessful) {
                        m_controller.logDebug("Ignoring %s update %s => %s because it failed previously!", type, fromVersion, toVersion);
                        return false;
                    }
                }

                m_controller.logDebug("Need to install update: newer %s version available!", type);
                return true;
            }
            else {
                m_controller.logDebug("No need to install update: no newer %s version available!", type);
                return false;
            }
        }
    }

    private volatile ScheduledFuture<?> m_scheduledFuture;
    private volatile UpdateInstaller m_updateInstaller;

    private final AtomicBoolean m_disabled;
    private final AtomicBoolean m_updateStreaming;
    private final AtomicBoolean m_fixPackage;
    private final AtomicLong m_maxRetries;
    private final AtomicLong m_interval;
    private final AtomicLong m_syncDelay;

    public DefaultController() {
        super("controller");

        m_disabled = new AtomicBoolean(false);
        m_interval = new AtomicLong(60);
        m_syncDelay = new AtomicLong(5);

        m_updateStreaming = new AtomicBoolean(true);
        m_fixPackage = new AtomicBoolean(true);
        m_maxRetries = new AtomicLong(1);
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            String value = payload.get(CONFIG_CONTROLLER_DISABLED);
            if (value != null && !"".equals(value)) {
                m_disabled.set(Boolean.parseBoolean(value));
            }

            value = payload.get(CONFIG_CONTROLLER_STREAMING);
            if (value != null && !"".equals(value)) {
                m_updateStreaming.set(Boolean.parseBoolean(value));
            }

            value = payload.get(CONFIG_CONTROLLER_FIXPACKAGES);
            if (value != null && !"".equals(value)) {
                m_fixPackage.set(Boolean.parseBoolean(value));
            }

            value = payload.get(CONFIG_CONTROLLER_SYNCDELAY);
            if (value != null && !"".equals(value)) {
                try {
                    m_syncDelay.set(Long.parseLong(value));
                }
                catch (NumberFormatException exception) {
                    // Ignore...
                }
            }

            value = payload.get(CONFIG_CONTROLLER_RETRIES);
            if (value != null && !"".equals(value)) {
                try {
                    m_maxRetries.set(Long.parseLong(value));
                }
                catch (NumberFormatException exception) {
                    // Ignore...
                }
            }

            value = payload.get(CONFIG_CONTROLLER_SYNCINTERVAL);
            if (value != null && !"".equals(value)) {
                try {
                    m_interval.set(Long.parseLong(value));
                }
                catch (NumberFormatException exception) {
                    // Ignore...
                }
            }

            logDebug("Config changed: disabled: %s, update: %s, fixPkg: %s, syncDelay: %d, syncInterval: %d, maxRetries: %d", m_disabled.get(), m_updateStreaming.get(), m_fixPackage.get(), m_syncDelay.get(), m_interval.get(), m_maxRetries.get());
        }
    }

    @Override
    public void run() {
        boolean disabled = m_disabled.get();
        long interval = m_interval.get();

        if (disabled) {
        	logDebug("Controller disabled by configuration. Skipping...");
        	return;
        }
        logDebug("Controller syncing...");
        try {
        	runFeedback();
        	try {
        		runAgentUpdate();
        	}
        	catch (IOException e) {
                logError("Agent update aborted due to Exception.", e);
        	}
        	try {
        		runDeploymentUpdate();
        	}
        	catch (IOException e) {
                logError("Deployment update aborted due to Exception.", e);
        	}
        	logDebug("Sync completed. Rescheduled in %d seconds", interval);
        }
        catch (RetryAfterException e) {
            // any method may throw this causing the sync to abort. The server is busy so no sense in trying
            // anything else until the retry window has passed.
            interval = e.getBackoffTime();
            logWarning("Sync received retry exception from server. Rescheduled in %d seconds", e.getBackoffTime());
        }
        finally {
            scheduleRun(interval);
        }
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStart() throws Exception {
        long delay = m_syncDelay.get();

        scheduleRun(delay);

        logDebug("Controller scheduled to run in %d seconds", delay);
    }

    @Override
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);

        if (m_updateInstaller != null) {
            m_updateInstaller.reset();
        }

        unscheduleRun();
    }

    protected void scheduleRun(long seconds) {
        unscheduleRun();

        ScheduledExecutorService executor = getExecutorService();
        if (executor.isShutdown()) {
            logWarning("Cannot schedule controller task, executor is shut down!");
        }
        else {
            m_scheduledFuture = executor.schedule(this, seconds, TimeUnit.SECONDS);
        }
    }

    protected void sendDeploymentCompletedEvent(UpdateInfo updateInfo, boolean success) {
        Map<String, String> eventProps = new HashMap<String, String>();
        eventProps.put("type", updateInfo.m_type);
        eventProps.put("name", getIdentificationHandler().getAgentId());
        eventProps.put("fromVersion", updateInfo.m_from.toString());
        eventProps.put("toVersion", updateInfo.m_to.toString());
        eventProps.put("fixPackage", Boolean.toString(updateInfo.m_fixPackage));
        eventProps.put("successful", Boolean.toString(success));

        getEventsHandler().postEvent(AGENT_INSTALLATION_COMPLETE, eventProps);
    }

    protected void sendDeploymentInstallEvent(UpdateInfo updateInfo) {
        Map<String, String> eventProps = new HashMap<String, String>();
        eventProps.put("type", updateInfo.m_type);
        eventProps.put("name", getIdentificationHandler().getAgentId());
        eventProps.put("fromVersion", updateInfo.m_from.toString());
        eventProps.put("toVersion", updateInfo.m_to.toString());
        eventProps.put("fixPackage", Boolean.toString(updateInfo.m_fixPackage));

        getEventsHandler().postEvent(AGENT_INSTALLATION_START, eventProps);
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

    private Set<String> getFeedbackChannelNames() {
        try {
            return getFeedbackHandler().getChannelNames();
        }
        catch (IOException e) {
            // Probably a serious problem due to local IO related to feedback. No cause to abort the sync so we just log
            // it as an error.
            logError("Exception while looking up feedback channel names.");
        }
        return Collections.emptySet();
    }

    private UpdateInstaller getUpdateInstaller() {
        boolean updateUsingStreams = m_updateStreaming.get();
        if (updateUsingStreams) {
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

    private void runAgentUpdate() throws RetryAfterException, IOException {
        logDebug("Checking for agent updates...");

        long maxRetries = m_maxRetries.get();
        boolean fixPackage = m_fixPackage.get();

        UpdateInstaller updateInstaller = getUpdateInstaller();
        updateInstaller.installUpdate(getAgentUpdateHandler(), fixPackage, maxRetries);
    }

    private void runDeploymentUpdate() throws RetryAfterException, IOException {
        logDebug("Checking for deployment updates...");

        long maxRetries = m_maxRetries.get();
        boolean fixPackage = m_fixPackage.get();

        UpdateInstaller updateInstaller = getUpdateInstaller();
        updateInstaller.installUpdate(getDeploymentHandler(), fixPackage, maxRetries);
    }

    private void runFeedback() throws RetryAfterException {
        Set<String> names = getFeedbackChannelNames();

        logDebug("Synchronizing feedback channels: %s", names);

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
                    logWarning("Exception while sending feedback for channel: %s", e, name);
                }
            }
        }
    }

    private void unscheduleRun() {
        if (m_scheduledFuture != null)
            m_scheduledFuture.cancel(false /* mayInterruptWhileRunning */);
    }
}
