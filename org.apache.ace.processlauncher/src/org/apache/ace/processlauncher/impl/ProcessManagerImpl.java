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
package org.apache.ace.processlauncher.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLifecycleListener;
import org.apache.ace.processlauncher.ProcessStreamListener;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;

/**
 * Manager for launched processes.
 */
public class ProcessManagerImpl implements ProcessManager {

    private final Map<String, ProcessLauncher> m_runningProcesses;

    private volatile DependencyManager m_dependencyManager;
    private volatile ProcessStateUpdater m_reaper;
    private volatile LogService m_logger;

    /**
     * Creates a new {@link ProcessManagerImpl} instance.
     */
    public ProcessManagerImpl() {
        m_runningProcesses = new HashMap<String, ProcessLauncher>();
    }

    /**
     * {@inheritDoc}
     */
    public int getRunningProcessesCount() throws IOException {
        int result = 0;
        synchronized (m_runningProcesses) {
            updateAdministration();
            result = m_runningProcesses.size();
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void launch(final String pid, final LaunchConfiguration launchConfiguration) throws IOException {
        // If this is the first time we're being called, make sure there's a
        // reaper task up and running...
        if (m_reaper == null || !m_reaper.isAlive()) {
            m_reaper = new ProcessStateUpdater();
            m_reaper.start();
        }

        // Create the process launcher service...
        ProcessLauncher launcher = createProcessLauncher(launchConfiguration);

        // Update our administration...
        ProcessLauncher oldProcess = null;
        synchronized (m_runningProcesses) {
            oldProcess = m_runningProcesses.put(pid, launcher);
        }

        // Clean up any old processes...
        killProcess(oldProcess);
        // Submit it for execution...
        launcher.run();
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() throws IOException {
        synchronized (m_runningProcesses) {
            // Cancel/kill all ongoing processes...
            for (ProcessLauncher launcher : m_runningProcesses.values()) {
                killProcess(launcher);
            }
            m_runningProcesses.clear();
        }

        if (m_reaper != null) {
            m_reaper.interrupt();
            try {
                m_reaper.join();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                m_reaper = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void terminate(final String pid) {
        ProcessLauncher launcher = null;
        synchronized (m_runningProcesses) {
            launcher = m_runningProcesses.remove(pid);
        }
        killProcess(launcher);
    }

    /**
     * Updates the administration by cleaning up all future's that are finished.
     * 
     * @throws IOException in case of I/O problems.
     */
    final void updateAdministration() throws IOException {
        synchronized (m_runningProcesses) {
            List<String> pids = new ArrayList<String>(m_runningProcesses.keySet());
            for (String pid : pids) {
                ProcessLauncher launcher = m_runningProcesses.get(pid);
                if (launcher.getExitValue() != null) {

                    String logLine =
                        String.format("Process %s (%s) terminated with code %d." + " Removing it from administration.",
                            pid, launcher.getLaunchConfiguration().getExecutableName(), launcher.getExitValue());
                    m_logger.log(LogService.LOG_DEBUG, logLine);

                    launcher.cleanup();

                    m_runningProcesses.remove(pid);
                    // Take care of the termination; should it be relaunched?!
                    if (processNeedsToBeRespawned(pid, launcher)) {
                        launch(pid, launcher.getLaunchConfiguration());
                    }
                }
            }
        }
    }

    /**
     * Factory method for creating a {@link ProcessLauncher} instance.
     * 
     * @param launchConfiguration the launch configuration to create a launch configuration for,
     *        cannot be <code>null</code>.
     * @return a new {@link ProcessLauncher} instance, never <code>null</code>.
     * @throws IOException in case the {@link ProcessLauncher} failed to instantiate.
     */
    private ProcessLauncher createProcessLauncher(LaunchConfiguration launchConfiguration) throws IOException {
        ProcessLauncher processLauncher = new ProcessLauncher(launchConfiguration);

        String lcFilter = launchConfiguration.getProcessLifecycleListener();
        String psFilter = launchConfiguration.getProcessStreamListener();

        // Create the proper service dependencies for the process launcher...
        if (lcFilter != null || psFilter != null) {
            Component comp = m_dependencyManager.createComponent().setImplementation(processLauncher);

            if (psFilter != null) {
                comp.add(m_dependencyManager.createServiceDependency()
                    .setService(ProcessStreamListener.class, psFilter).setRequired(false));
            }

            if (lcFilter != null) {
                comp.add(m_dependencyManager.createServiceDependency()
                    .setService(ProcessLifecycleListener.class, lcFilter).setRequired(false));
            }

            m_dependencyManager.add(comp);
        }

        return processLauncher;
    }

    /**
     * Determines whether or not the given exit value is "normal" indicating a successful or
     * non-successful termination.
     * 
     * @param exitValue the process exit value, as integer value, can be <code>null</code>.
     * @return <code>true</code> if the process is non-successfully terminated, <code>false</code>
     *         if the processes terminated successfully.
     */
    private boolean isNonSuccessfullyTerminated(LaunchConfiguration config, Integer exitValue) {
        return (exitValue != null) && (config.getNormalExitValue() != exitValue);
    }

    /**
     * Cancels a given future, if it is not already completed its task.
     * 
     * @param launcher the process launcher to cancel, can be <code>null</code> in which case this
     *        method does nothing.
     */
    private void killProcess(final ProcessLauncher launcher) {
        if (launcher != null) {
            String logLine =
                String.format("Killing process (%s)...", launcher.getLaunchConfiguration().getExecutableName());
            m_logger.log(LogService.LOG_INFO, logLine);

            launcher.kill();
        }
    }

    /**
     * Handles a given terminated process, which might need to be relaunched if it is not cleanly
     * terminated for example.
     * 
     * @param pid the PID of the process that was launched, cannot be <code>null</code>;
     * @param launcher the terminated process to handle, cannot be <code>null</code>.
     * @throws IOException in case of I/O problems during the respawn of a terminated process.
     */
    private boolean processNeedsToBeRespawned(String pid, ProcessLauncher launcher) throws IOException {
        LaunchConfiguration config = launcher.getLaunchConfiguration();
        Integer exitValue = launcher.getExitValue();

        // Is the process non-successfully terminated?
        if (isNonSuccessfullyTerminated(config, exitValue)) {
            // If so, does it need to be respawned?
            if (config.isRespawnAutomatically()) {
                // We need to respawn the process automatically!
                String logLine =
                    String.format("Process %s (%s) terminated with value %d;" + " respawning it as requested...", pid,
                        config.getExecutableName(), exitValue);
                m_logger.log(LogService.LOG_INFO, logLine);

                // Simply relaunch the process again...
                return true;
            }
            else {
                // Don't bother restarting the process...
                String logLine =
                    String.format("Process %s (%s) terminated with value %d.", pid, config.getExecutableName(),
                        exitValue);
                m_logger.log(LogService.LOG_INFO, logLine);
            }
        }
        else {
            // Process ended normally...
            String logLine = String.format("Process %s (%s) terminated normally.", pid, config.getExecutableName());
            m_logger.log(LogService.LOG_INFO, logLine);
        }

        return false;
    }

    /**
     * Ensures that periodically the completed processes are removed from the administration.
     */
    final class ProcessStateUpdater extends Thread {
        /**
         * The number of milliseconds to wait before updating the status of all running processes.
         */
        private static final int DELAY = 100;

        /**
         * Creates a new {@link ProcessStateUpdater} instance.
         */
        public ProcessStateUpdater() {
            super("Process state update thread");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    Thread.sleep(DELAY);

                    m_logger.log(LogService.LOG_DEBUG, "Updating process administration...");

                    // Update the administration...
                    updateAdministration();
                }
                catch (InterruptedException e) {
                    // Update the current thread's administration!
                    Thread.currentThread().interrupt();
                }
                catch (IOException e) {
                    m_logger.log(LogService.LOG_WARNING, "Respawn failed!", e);
                }
            }
        }
    }

}
