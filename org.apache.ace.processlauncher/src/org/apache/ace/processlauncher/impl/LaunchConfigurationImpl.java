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

import java.io.File;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLifecycleListener;
import org.apache.ace.processlauncher.ProcessStreamListener;

/**
 * Denotes a launch configuration, describing what and how a process should be launched.
 */
public final class LaunchConfigurationImpl implements LaunchConfiguration {
    /** The convention is to use zero as normal exit value. */
    private static final int NORMAL_EXIT_VALUE = 0;

    /**
     * Denotes the number of instances that will be launched for this configuration.
     */
    private final int m_instanceCount;
    /** Denotes the full path-name to the executable to launch. */
    private final String m_executableName;
    /** Denotes the arguments to pass to the executable. */
    private final String[] m_executableArgs;
    /**
     * The process stream listener that should be called for interaction with the process.
     */
    private final String m_processStreamListenerFilter;
    /**
     * The process lifecycle listener that should be called for the lifecycle changes of the
     * process.
     */
    private final String m_processLifecycleListenerFilter;
    /** Whether or not we should respawn the process once it died. */
    private final boolean m_respawnAutomatically;
    /** Which directory should be set before launching the executable. */
    private final File m_workingDirectory;
    /**
     * What exit-value is to be considered "normal" for this process? By convention, this is 0.
     */
    private final int m_normalExitValue;

    /**
     * Creates a new {@link LaunchConfigurationImpl} instance.
     * 
     * @param instanceCount the number of instances to launch, >= 1;
     * @param workingDirectory the optional working directory to use, can be <code>null</code> if
     *        the default working directory should be used;
     * @param executableName the full path-name to the executable to launch, cannot be
     *        <code>null</code> or empty;
     * @param executableArgs the optional arguments to pass to the executable, can be
     *        <code>null</code>;
     * @param normalExitValue the "normal" exit value to determine whether or not the process has
     *        terminated normally;
     * @param processStreamListenerFilter denotes the filter to use to obtain the
     *        {@link ProcessStreamListener} to redirect the process input/output to, can be
     *        <code>null</code> if no interaction is desired;
     * @param processLifecycleListenerFilter denotes the filter to use to obtain the
     *        {@link ProcessLifecycleListener};
     * @param respawnAutomatically <code>true</code> if the process should be respawned
     *        automatically upon non-zero exit codes.
     */
    public LaunchConfigurationImpl(int instanceCount, String workingDirectory, String executableName,
        String[] executableArgs, int normalExitValue, String processStreamListenerFilter,
        String processLifecycleListenerFilter, boolean respawnAutomatically) {
        if (instanceCount <= 0) {
            throw new IllegalArgumentException("Invalid instance count!");
        }
        if (executableName == null || executableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid executable name!");
        }
        if (executableArgs == null) {
            throw new IllegalArgumentException("Invalid executable args!");
        }
        m_instanceCount = instanceCount;
        m_workingDirectory =
            (workingDirectory == null || workingDirectory.trim().isEmpty()) ? null : new File(workingDirectory);
        m_executableName = executableName;
        m_executableArgs = executableArgs;
        m_normalExitValue = normalExitValue;
        m_processStreamListenerFilter = processStreamListenerFilter;
        m_processLifecycleListenerFilter = processLifecycleListenerFilter;
        m_respawnAutomatically = respawnAutomatically;
    }

    /**
     * Creates a new {@link LaunchConfigurationImpl} instance.
     * 
     * @param instanceCount the number of instances to launch, >= 1;
     * @param executableName the full path-name to the executable to launch, cannot be
     *        <code>null</code> or empty;
     * @param executableArgs the optional arguments to pass to the executable, can be
     *        <code>null</code>;
     * @param processStreamListenerFilter denotes the filter to use to obtain the
     *        {@link ProcessStreamListener} to redirect the process input/output to, can be
     *        <code>null</code> if no interaction is desired;
     * @param respawnAutomatically <code>true</code> if the process should be respawned
     *        automatically upon non-zero exit codes.
     */
    public LaunchConfigurationImpl(int instanceCount, String executableName, String[] executableArgs,
        String processStreamListenerFilter, boolean respawnAutomatically) {
        this(instanceCount, null, executableName, executableArgs, NORMAL_EXIT_VALUE, processStreamListenerFilter, null,
            respawnAutomatically);
    }

    /**
     * {@inheritDoc}
     */
    public String[] getCommandLine() {
        int size = this.m_executableArgs.length;
        String[] result = new String[1 + size];

        result[0] = this.m_executableName;
        if (size > 0) {
            System.arraycopy(this.m_executableArgs, 0, result, 1, size);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    public String[] getExecutableArgs() {
        return m_executableArgs;
    }

    /**
     * {@inheritDoc}
     */
    public String getExecutableName() {
        return m_executableName;
    }

    /**
     * {@inheritDoc}
     */
    public int getInstanceCount() {
        return m_instanceCount;
    }

    /**
     * {@inheritDoc}
     */
    public int getNormalExitValue() {
        return m_normalExitValue;
    }

    /**
     * {@inheritDoc}
     */
    public File getWorkingDirectory() {
        return m_workingDirectory;
    }

    /**
     * {@inheritDoc}
     */
    public String getProcessStreamListener() {
        return m_processStreamListenerFilter;
    }

    /**
     * {@inheritDoc}
     */
    public String getProcessLifecycleListener() {
        return m_processLifecycleListenerFilter;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isRespawnAutomatically() {
        return m_respawnAutomatically;
    }
}
