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
import static org.apache.ace.agent.impl.ConnectionUtil.closeSilently;
import static org.apache.ace.agent.impl.InternalConstants.AGENT_CONFIG_CHANGED;
import static org.apache.ace.agent.impl.InternalConstants.AGENT_DEPLOYMENT_COMPLETE;
import static org.apache.ace.agent.impl.InternalConstants.AGENT_DEPLOYMENT_INSTALL;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;
import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentException;

/**
 * Default configurable controller
 */
public class DefaultController extends ComponentBase implements Runnable, EventListener {

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
        public void completed(DownloadResult result) {
            int delay = 1; // seconds
            m_downloadResult = result;
            getController().logInfo("Deployment package download completed for version %s. Rescheduling the controller to run in %d seconds", m_downloadVersion, delay);
            getController().scheduleRun(delay);
        }

        @Override
        public void doInstallUpdate(Version fromVersion, Version toVersion, boolean fixPackage) throws RetryAfterException, DeploymentException, IOException {
            DeploymentHandler deploymentHandler = getController().getDeploymentHandler();

            if (m_downloadHandle != null && !m_downloadVersion.equals(toVersion)) {
                getController().logInfo("Cancelling deployment package download for %s because a newer version is available...", m_downloadVersion);
                m_downloadHandle.discard();
                m_downloadHandle = null;
            }

            if (m_downloadHandle == null) {
                getController().logInfo("Starting deployment package download %s => %s...", fromVersion, toVersion);

                m_downloadVersion = toVersion;

                m_downloadHandle = deploymentHandler.getDownloadHandle(toVersion, fixPackage);
                m_downloadHandle.setProgressListener(this).setCompletionListener(this).start();
                return;
            }

            if (m_downloadResult == null) {
                getController().logInfo("Deployment package download for %s is in progress %d / %d", toVersion, m_downloadProgress, m_downloadLength);
                return;
            }

            DownloadState state = m_downloadResult.getState();
            if (DownloadState.FAILED == state) {
                getController().logWarning("Deployment package download for %s is FAILED. Restarting download...");

                m_downloadHandle.discard();
                m_downloadHandle = null;

                throw new IOException("Download failed for deployment update " + fromVersion + " => " + toVersion);
            }
            else if (DownloadState.STOPPED == state) {
                getController().logInfo("Deployment package download for %s is STOPPED. Trying to resume download...");

                m_downloadResult = null;
                m_downloadHandle.start();
            }
            else if (DownloadState.SUCCESSFUL == state) {
                getController().logInfo("Installing downloaded deployment update %s => %s...", fromVersion, toVersion);

                getController().sendDeploymentInstallEvent(fromVersion, toVersion, fixPackage);
                
                InputStream inputStream = null;
                boolean success = false;

                try {
                    inputStream = m_downloadResult.getInputStream();

                    deploymentHandler.deployPackage(inputStream);

                    success = true;
                }
                finally {
                    m_downloadHandle.discard();
                    m_downloadHandle = null;

                    closeSilently(inputStream);

                    getController().sendDeploymentCompletedEvent(fromVersion, toVersion, fixPackage, success);
                }
            }
        }

        @Override
        public void doReset() {
            if (m_downloadHandle != null) {
                getController().logInfo("Cancelling deployment package download for version %s because of reset...", m_downloadVersion);
                m_downloadHandle.discard();
            }
            clearDownloadState();
        }

        @Override
        public void progress(long contentLength, long progress) {
            m_downloadLength = contentLength;
            m_downloadProgress = progress;
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

            getController().sendDeploymentInstallEvent(from, to, fix);

            DeploymentHandler deploymentHandler = getController().getDeploymentHandler();

            InputStream inputStream = null;
            boolean success = false;

            try {
                inputStream = deploymentHandler.getInputStream(to, fix);

                deploymentHandler.deployPackage(inputStream);

                success = true;
            }
            finally {
                closeSilently(inputStream);

                getController().sendDeploymentCompletedEvent(from, to, fix, success);
            }
        }

        @Override
        protected void doReset() {
            // Nop
        }
    }

    /**
     * Base class for internal installer strategies. This implementation handles max update retry contraints and
     * delegates the rest to concrete implementations.
     */
    abstract static class UpdateInstaller {
        private final DefaultController m_controller;
        private Version m_lastVersionTried = null;
        private boolean m_lastVersionSuccessful = true;
        private int m_failureCount = 0;

        public UpdateInstaller(DefaultController controller) {
            m_controller = controller;
        }

        public final boolean canInstallUpdate(Version fromVersion, Version toVersion, long maxRetries) {
            if (toVersion.compareTo(fromVersion) > 0) {
                // Possible newer version, lets check our administration whether we actually need to do something...
                if (m_lastVersionTried != null && toVersion.equals(m_lastVersionTried)) {
                    if (m_failureCount >= maxRetries) {
                        m_controller.logDebug("Ignoring update %s => %s because max retries (%d) reached!", fromVersion, toVersion, maxRetries);
                        return false;
                    }
                    if (!m_lastVersionSuccessful) {
                        m_controller.logDebug("Ignoring update %s => %s because it failed previously!", fromVersion, toVersion);
                        return false;
                    }
                }

                m_controller.logDebug("Need to install update: newer deployment version available!");
                return true;
            }
            else {
                m_controller.logDebug("No need to install update: no newer deployment version available!");
                return false;
            }
        }

        public final void installUpdate(Version fromVersion, Version toVersion, boolean fixPackage) throws RetryAfterException {
            if (m_lastVersionTried == null || !toVersion.equals(m_lastVersionTried)) {
                m_lastVersionTried = toVersion;
                m_lastVersionSuccessful = true;
                m_failureCount = 0;
            }

            try {
                doInstallUpdate(fromVersion, toVersion, fixPackage);
                // As we've just successfully installed this update, reset the failure counter...
                m_failureCount = 0;
                m_lastVersionSuccessful = true;
            }
            catch (RetryAfterException e) {
                // The server is busy. Re-throw so the controller can abort the sync and reschedule.
                throw (e);
            }
            catch (DeploymentException e) {
                getController().logWarning("Exception while deploying the package", e);
                e.printStackTrace();
                m_failureCount++;
                m_lastVersionSuccessful = false;
            }
            catch (IOException e) {
                getController().logWarning("Exception opening/streaming package inputstream", e);
                e.printStackTrace();
                m_failureCount++;
            }
        }

        public final void reset() {
            m_lastVersionTried = null;
            m_failureCount = 0;
            doReset();
        }

        protected abstract void doInstallUpdate(Version from, Version to, boolean fix) throws RetryAfterException, DeploymentException, IOException;

        protected abstract void doReset();

        protected final DefaultController getController() {
            return m_controller;
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
        if (AGENT_CONFIG_CHANGED.equals(topic)) {
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

        try {
            if (disabled) {
                logDebug("Controller disabled by configuration. Skipping...");
                return;
            }

            logDebug("Controller syncing...");

            runFeedback();
            runAgentUpdate();
            runDeploymentUpdate();

            logDebug("Sync completed. Rescheduled in %d seconds", interval);
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
        m_scheduledFuture = getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    protected void sendDeploymentCompletedEvent(Version from, Version to, boolean fixPackage, boolean success) {
        Map<String, String> eventProps = new HashMap<String, String>();
        eventProps.put("name", getIdentificationHandler().getAgentId());
        eventProps.put("fromVersion", from.toString());
        eventProps.put("toVersion", to.toString());
        eventProps.put("fixPackage", Boolean.toString(fixPackage));
        eventProps.put("successful", Boolean.toString(success));

        getEventsHandler().postEvent(AGENT_DEPLOYMENT_COMPLETE, eventProps);
    }

    protected void sendDeploymentInstallEvent(Version from, Version to, boolean fixPackage) {
        Map<String, String> eventProps = new HashMap<String, String>();
        eventProps.put("name", getIdentificationHandler().getAgentId());
        eventProps.put("fromVersion", from.toString());
        eventProps.put("toVersion", to.toString());
        eventProps.put("fixPackage", Boolean.toString(fixPackage));

        getEventsHandler().postEvent(AGENT_DEPLOYMENT_INSTALL, eventProps);
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

    private Version getHighestAvailableAgentVersion() throws RetryAfterException {
        SortedSet<Version> available = new TreeSet<Version>();
        try {
            available = getAgentUpdateHandler().getAvailableVersions();
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we just
            // log it as a warning.
            logWarning("Exception while retrieving agent versions", e);
        }

        return getHighestVersion(available);
    }

    private Version getHighestAvailableDeploymentVersion() throws RetryAfterException {
        SortedSet<Version> available = new TreeSet<Version>();
        try {
            available = getDeploymentHandler().getAvailableVersions();
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we just
            // log it as a warning.
            logWarning("Exception while retrieving deployment versions", e);
        }

        return getHighestVersion(available);
    }

    private Version getHighestVersion(SortedSet<Version> available) {
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        return highest;
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

    private void runAgentUpdate() throws RetryAfterException {
        logDebug("Checking for agent updates...");

        AgentUpdateHandler agentUpdateHandler = getAgentUpdateHandler();

        Version current = agentUpdateHandler.getInstalledVersion();
        Version highest = getHighestAvailableAgentVersion();

        if (highest.compareTo(current) < 1) {
            logDebug("No agent update available for version %s", current);
            return;
        }

        logInfo("Installing agent update %s => %s", current, highest);

        InputStream inputStream = null;
        try {
            inputStream = agentUpdateHandler.getInputStream(highest);
            agentUpdateHandler.install(inputStream);
        }
        catch (IOException e) {
            // Hopefully temporary problem due to remote IO or configuration. No cause to abort the sync so we
            // just log it as a warning.
            // FIXME Does not cover failed updates and should handle retries
            logWarning("Exception while installing agent update %s", e, highest);
        }
        finally {
            closeSilently(inputStream);
        }
    }

    private void runDeploymentUpdate() throws RetryAfterException {
        logDebug("Checking for deployment updates...");

        DeploymentHandler deploymentHandler = getDeploymentHandler();

        Version current = deploymentHandler.getInstalledVersion();
        Version highest = getHighestAvailableDeploymentVersion();

        long maxRetries = m_maxRetries.get();
        boolean updateUsingStreams = m_updateStreaming.get();
        
        UpdateInstaller updateInstaller = getUpdateInstaller(updateUsingStreams);
        if (updateInstaller.canInstallUpdate(current, highest, maxRetries)) {
            boolean fixPackage = m_fixPackage.get();

            updateInstaller.installUpdate(current, highest, fixPackage);
        }
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

    private void unscheduleRun() {
        if (m_scheduledFuture != null)
            m_scheduledFuture.cancel(false /* mayInterruptWhileRunning */);
    }
}
