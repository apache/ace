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
package org.apache.ace.agent.itest;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandle.DownloadProgressListener;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.agent.UpdateHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * Tests that we can create an agent with a completely custom controller, see {@link CustomContextAwareController} for
 * more information about the actual implementation.
 * 
 * @see CustomContextAwareController
 */
public class CustomAgentControllerTest extends BaseAgentControllerTest {
    /**
     * The actual custom controller as {@link Runnable} task, that simply loops and executes its tasks until notified to
     * stop.
     * 
     * @see #run()
     */
    public static class CustomContextAwareController implements AgentContextAware, Runnable {
        private volatile AgentContext m_agentContext;
        private volatile BundleContext m_bundleContext;
        private volatile AgentUser m_agentUser;

        @Override
        public void init(AgentContext agentContext) throws Exception {
            m_agentContext = agentContext;

            m_bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
            m_bundleContext.registerService(AgentUser.class.getName(), new AcknowledgingAgentUser(), null);
        }

        /**
         * Main loop, will sleep for a little and once every 500 ms will do the following:
         * <ol>
         * <li>Synchronize all agent feedback with the server (see {@link #sendFeedbackToServer()});</li>
         * <li>Check for agent updates (see {@link #checkForUpdate(UpdateType)});</li>
         * <li>Check for deployment updates (see {@link #checkForUpdate(UpdateType)}).</li>
         * </ol>
         * <p>
         * Note that this implementation does very little error checking and is rather stubborn when it comes across
         * failures: it simply keeps retrying, which, for this use case, is acceptable.
         * </p>
         * 
         * @see #stop()
         * @see #checkForUpdate(UpdateType)
         * @see #sendFeedbackToServer()
         */
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException exception) {
                    // We're requested to stop...
                    Thread.currentThread().interrupt();
                    break;
                }

                if (Thread.currentThread().isInterrupted()) {
                    // Check once more whether we're not stopped while sleeping...
                    break;
                }

                sendFeedbackToServer();

                checkForUpdate(UpdateType.AGENT);

                checkForUpdate(UpdateType.DEPLOYMENT);
            }
        }

        @Override
        public void start(AgentContext agentContext) throws Exception {
            logInfo("Custom controller running...");

            ServiceReference<AgentUser> serviceRef = m_bundleContext.getServiceReference(AgentUser.class);
            if (serviceRef != null) {
                m_agentUser = m_bundleContext.getService(serviceRef);
            }
            else {
                throw new IllegalStateException("No agent user service registered?!");
            }
        }

        /**
         * Stops the main loop and allows the {@link #run()} loop to terminate (after it has done all of its work).
         */
        public void stop() {
            logInfo("Custom controller stopping...");
        }

        /**
         * Does the actual check for either the agent or deployment updates, and if available:
         * <ol>
         * <li>asks the "user" whether it should download this update, and if so;</li>
         * <li>downloads the update to a temporary location;</li>
         * <li>if the download is complete, it asks the "user" whether it should proceed with installing it, and if so;</li>
         * <li>installs the agent/deployment update.</li>
         * </ol>
         * <p>
         * In case an exception occurs during this check, it is logged and the method returns (early). No exceptions are
         * propagated. In production code, a little more sophisticated error checking should be performed.
         * </p>
         * 
         * @param updateType
         *            the type of update we're performing, cannot be <code>null</code>.
         */
        private void checkForUpdate(UpdateType updateType) {
            try {
                UpdateHandler updateHandler = getUpdateHandler(updateType);

                Version installed = updateHandler.getInstalledVersion();
                Version available = updateHandler.getHighestAvailableVersion();
                if (installed != null && installed.compareTo(available) < 0) {
                    // Update available, ask the user whether we should download it...
                    if (!m_agentUser.downloadAvailableUpdate(updateType, getAgentId(), installed, available)) {
                        // No, we may not download this update now...
                        return;
                    }

                    logInfo("Downloading %s update (from v%s to v%s)...", updateType, installed, available);

                    DownloadHandle downloadHandle = updateHandler.getDownloadHandle(available, false /* fixPackage */);

                    Future<DownloadResult> future = downloadHandle.start(new DownloadProgressListener() {
                        @Override
                        public void progress(long bytesRead) {
                            logInfo("Download progress: %d bytes read...", bytesRead);
                        }
                    });
                    // Block until the download is complete...
                    DownloadResult result = future.get();

                    // Download is complete, ask the user once more if we're allowed to install the update...
                    if (m_agentUser.installAvailableUpdate(updateType, getAgentId(), installed, available)) {
                        logInfo("Installing %s update (from v%s to v%s)...", updateType, installed, available);

                        // We've confirmation that we can install this update...
                        updateHandler.install(result.getInputStream());
                    }

                    // Throw away downloaded packages...
                    downloadHandle.discard();
                }
            }
            catch (Exception exception) {
                logWarning("%s update failed with %s.", exception, updateType, exception.getMessage());
                exception.printStackTrace(System.out);
            }
        }

        /**
         * @return the identification of the current agent, as returned by the agent's API.
         */
        private String getAgentId() {
            return m_agentContext.getHandler(IdentificationHandler.class).getAgentId();
        }

        /**
         * Returns the update handler for the given {@link UpdateType}.
         * 
         * @param updateType
         *            the type of update we want an update handler for, cannot be <code>null</code>.
         * @return an {@link UpdateHandler} instance, never <code>null</code>.
         */
        private UpdateHandler getUpdateHandler(UpdateType updateType) {
            UpdateHandler updateHandler;
            if (UpdateType.AGENT == updateType) {
                updateHandler = m_agentContext.getHandler(AgentUpdateHandler.class);
            }
            else {
                updateHandler = m_agentContext.getHandler(DeploymentHandler.class);
            }
            return updateHandler;
        }

        private void logInfo(String msg, Object... args) {
            m_agentContext.getHandler(LoggingHandler.class).logInfo("CustomController", msg, null, args);
        }

        private void logWarning(String msg, Exception ex, Object... args) {
            m_agentContext.getHandler(LoggingHandler.class).logWarning("CustomController", msg, ex, args);
        }

        /**
         * Synchronizes the agent's feedback with the server by retrieving all feedback channels and sending their
         * feedback to the server in turn.
         * <p>
         * In case an exception occurs during this check, it is logged and the method returns (early). No exceptions are
         * propagated. In production code, a little more sophisticated error checking should be performed.
         * </p>
         */
        private void sendFeedbackToServer() {
            try {
                FeedbackHandler feedbackHandler = m_agentContext.getHandler(FeedbackHandler.class);
                Set<String> channelNames = feedbackHandler.getChannelNames();
                for (String channelName : channelNames) {
                    FeedbackChannel channel = feedbackHandler.getChannel(channelName);

                    logInfo("Synchronizing feedback of %s with server...", channelName);

                    channel.sendFeedback();
                }
            }
            catch (Exception exception) {
                logWarning("Feedback synchronization failed with %s.", exception, exception.getMessage());
            }
        }
    }

    /**
     * Creates a new {@link CustomAgentControllerTest} instance.
     */
    public CustomAgentControllerTest() {
        super(CustomContextAwareController.class.getName(), "1", "0.0.1");
    }

    /**
     * Tests that we can provide a custom controller implementation based on the following use-case:
     * <p>
     * The agent should check for updates, and if found, ask the user whether it should proceed to download this update.
     * If confirmed, the download of the update is started, and when complete, the user is asked once more whether to
     * proceed with the installation of the update.
     * </p>
     * 
     * @see CustomContextAwareController
     */
    public void testCustomController() throws Exception {
        waitForInstalledVersion(m_agentControl.getDeploymentHandler(), m_dpVersion);
    }
}
