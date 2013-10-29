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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLauncherService;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * Provides a managed service factory for launching processes based on a certain launch
 * configuration.
 */
public class ProcessLauncherServiceImpl implements ManagedServiceFactory, ProcessLauncherService {

    private static final String NAME = "Launcher Service Factory";

    /** Contains all current launch configurations. */
    private final Map<String, LaunchConfiguration> m_launchConfigurations = new HashMap<String, LaunchConfiguration>();
    /** Manages all running processes for us. */
    private volatile ProcessManager m_processManager;
    private volatile LogService m_logger;

    /**
     * Returns whether or not a given PID is contained as launch configuration.
     * 
     * @param pid the PID to test for, cannot be <code>null</code>.
     * @return <code>true</code> if the given PID exists as launch configuration, <code>false</code>
     *         otherwise.
     */
    public boolean containsPid(String pid) {
        synchronized (m_launchConfigurations) {
            return m_launchConfigurations.containsKey(pid);
        }
    }

    /**
     * Called when a launch configuration with the given PID is removed from the config-admin.
     * 
     * @param pid the service PID that is to be deleted, never <code>null</code>.
     * @see org.osgi.service.cm.ManagedServiceFactory#deleted(java.lang.String)
     */
    public final void deleted(final String pid) {
        LaunchConfiguration oldLaunchConfig = null;

        synchronized (m_launchConfigurations) {
            oldLaunchConfig = m_launchConfigurations.remove(pid);
        }

        if (oldLaunchConfig != null) {
            terminateProcesses(pid, oldLaunchConfig);
        }
    }

    /**
     * {@inheritDoc}
     */
    public int getLaunchConfigurationCount() {
        synchronized (m_launchConfigurations) {
            return m_launchConfigurations.size();
        }
    }

    /**
     * Returns the symbolic name for this service factory.
     * 
     * @return a symbolic name, never <code>null</code>.
     * @see org.osgi.service.cm.ManagedServiceFactory#getName()
     */
    public final String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    public int getRunningProcessCount() throws IOException {
        if (m_processManager == null) {
            return 0;
        }
        return m_processManager.getRunningProcessesCount();
    }

    /**
     * Sets the logging service.
     * 
     * @param logger the log service to set, can be <code>null</code>.
     */
    public void setLogger(LogService logger) {
        m_logger = logger;
    }

    /**
     * Sets the process manager.
     * 
     * @param processManager the process manager to set, cannot be <code>null</code>.
     */
    public void setProcessManager(ProcessManager processManager) {
        m_processManager = processManager;
    }

    /**
     * Shuts down this service and terminates all running processes.
     * 
     * @throws IOException in case of problems shutting down processes.
     */
    public void shutdown() throws IOException {
        synchronized (m_launchConfigurations) {
            for (Map.Entry<String, LaunchConfiguration> entry : m_launchConfigurations.entrySet()) {
                terminateProcesses(entry.getKey(), entry.getValue());
            }
            m_launchConfigurations.clear();
        }
        // Shut down the process manager as well...
        m_processManager.shutdown();
    }

    /**
     * Called when a new configuration is added, or when an existing configuration is updated.
     * 
     * @param pid the service PID that is added/updated, never <code>null</code>;
     * @param config the service configuration that is added/updated, can be <code>null</code>.
     * @throws ConfigurationException in case the given service configuration is incorrect.
     * @see org.osgi.service.cm.ManagedServiceFactory#updated(java.lang.String,
     *      java.util.Dictionary)
     */
    @SuppressWarnings({ "rawtypes" })
    public final void updated(final String pid, final Dictionary config) throws ConfigurationException {
        LaunchConfiguration oldLaunchConfig = null;
        LaunchConfiguration newLaunchConfig = null;

        if (config != null) {
            newLaunchConfig = createLaunchConfiguration(config);
        }

        synchronized (m_launchConfigurations) {
            oldLaunchConfig = m_launchConfigurations.put(pid, newLaunchConfig);
        }

        if (oldLaunchConfig != null) {
            terminateProcesses(pid, oldLaunchConfig);
        }
        if (newLaunchConfig != null) {
            launchProcesses(pid, newLaunchConfig);
        }
    }

    /**
     * Converts the given properties to a complete launch configuration.
     * 
     * @param config the properties to convert to a launch configuration, cannot be
     *        <code>null</code>.
     * @return a {@link LaunchConfiguration} instance, never <code>null</code>.
     * @throws ConfigurationException in case an invalid configuration property was found.
     */
    private LaunchConfiguration createLaunchConfiguration(final Dictionary<Object, Object> config)
        throws ConfigurationException {
        return LaunchConfigurationFactory.create(config);
    }

    /**
     * Creates a process identifier based on the given identifier and value.
     * 
     * @param id the identifier part;
     * @param value the value part.
     * @return a process identifier, as String, never <code>null</code>.
     */
    private String createPID(final String id, int value) {
        return String.format("%s-%d", id, value);
    }

    /**
     * Executes a given launch configuration.
     * 
     * @param id the identifier of the launch configuration to execute, cannot be <code>null</code>
     *        or empty;
     * @param launchConfiguration the launch configuration to execute, cannot be <code>null</code>.
     */
    private void launchProcesses(final String id, final LaunchConfiguration launchConfiguration) {

        int count = launchConfiguration.getInstanceCount();
        while (count-- > 0) {
            String pid = createPID(id, count);
            try {
                m_processManager.launch(pid, launchConfiguration);

                m_logger.log(LogService.LOG_DEBUG, "Launched instance #" + count + " of process " + pid);
            }
            catch (IOException e) {
                m_logger.log(LogService.LOG_WARNING, "Process failed to launch!", e);
            }
        }
    }

    /**
     * Cleans up & terminates all processes of a given launch configuration.
     * 
     * @param id the identifier of the launch configuration to execute, cannot be <code>null</code>
     *        or empty;
     * @param launchConfiguration the launch configuration to clean up, cannot be <code>null</code>.
     */
    private void terminateProcesses(final String id, final LaunchConfiguration launchConfiguration) {

        int count = launchConfiguration.getInstanceCount();
        while (count-- > 0) {
            String pid = createPID(id, count);
            try {
                m_processManager.terminate(pid);

                m_logger.log(LogService.LOG_DEBUG, "Terminated instance #" + count + " of process " + pid);
            }
            catch (IOException e) {
                m_logger.log(LogService.LOG_WARNING, "Process failed to terminate!", e);
            }
        }
    }
}
