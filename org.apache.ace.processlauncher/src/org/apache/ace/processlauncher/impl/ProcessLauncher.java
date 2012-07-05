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
import java.util.Map;
import java.util.Properties;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLifecycleListener;
import org.apache.ace.processlauncher.ProcessStreamListener;
import org.apache.ace.processlauncher.util.InputStreamRedirector;

/**
 * Denotes a service that can launch a <em>single</em> process and possibly allows interaction with
 * it.
 * <p>
 * If for a process multiple instances should be launched, multiple {@link ProcessLauncher}
 * instances should be created!
 * </p>
 * 
 * @author Jan Willem Janssen <janwillem.janssen@luminis.eu>
 */
public class ProcessLauncher {

    private final LaunchConfiguration m_launchConfiguration;

    private volatile ProcessStreamListener m_processStreamListener;
    private volatile ProcessLifecycleListener m_processLifecycleListener;
    private volatile Process m_runningProcess;

    private InputStreamRedirector m_processStdoutRedirector;
    private InputStreamRedirector m_processStdinRedirector;

    /**
     * Creates a new {@link ProcessLauncher} instance which redirects all input/output of the
     * running process to the given streams.
     * 
     * @param launchConfiguration the launch configuration to use, cannot be <code>null</code>;
     * @throws IllegalArgumentException in case the given launch configuration was <code>null</code>
     *         .
     */
    public ProcessLauncher(LaunchConfiguration launchConfiguration) {
        if (launchConfiguration == null) {
            throw new IllegalArgumentException("Launch configuration cannot be null!");
        }
        m_launchConfiguration = launchConfiguration;
    }

    /**
     * Creates a new {@link ProcessLauncher} instance which redirects all input/output of the
     * running process to the given streams.
     * 
     * @param launchConfiguration the launch configuration to use, cannot be <code>null</code>;
     * @param processStreamListener the input stream to redirect the process input to, can be
     *        <code>null</code>;
     * @param processLifecycleListener the output stream to redirect the process output to, can be
     *        <code>null</code> if the process output is not to be redirected.
     * @throws IllegalArgumentException in case the given launch configuration was <code>null</code>
     *         .
     */
    public ProcessLauncher(LaunchConfiguration launchConfiguration, ProcessStreamListener processStreamListener,
        ProcessLifecycleListener processLifecycleListener) {
        if (launchConfiguration == null) {
            throw new IllegalArgumentException("Launch configuration cannot be null!");
        }
        m_launchConfiguration = launchConfiguration;
        m_processStreamListener = processStreamListener;
        m_processLifecycleListener = processLifecycleListener;
    }

    /**
     * If the process is finished, returns its exit value.
     * 
     * @return the process' exit value, or <code>null</code> if the process is still running.
     * @see #isAlive()
     */
    public Integer getExitValue() {
        // runningProcess can only be null if #run() is not yet called!
        if ((m_runningProcess == null) || isAlive()) {
            return null;
        }

        return m_runningProcess.exitValue();
    }

    /**
     * Returns the launch configuration for this process launcher.
     * 
     * @return the launch configuration, never <code>null</code>.
     */
    public LaunchConfiguration getLaunchConfiguration() {
        return m_launchConfiguration;
    }

    /**
     * Call to clean up the administration of this process launcher, and to invoke the proper
     * lifecycle methods on any interested listener.
     * 
     * @throws IllegalStateException in case the process is still alive.
     * @see #isAlive()
     */
    public void cleanup() throws IllegalStateException {
        if (isAlive()) {
            throw new IllegalStateException("Process is still alive; cannot clean up!");
        }

        if (m_processLifecycleListener != null) {
            m_processLifecycleListener.afterProcessEnd(m_launchConfiguration);
        }

        closeProcessStreamRedirects();
    }

    /**
     * Kills any running processes and updates the internal administration (even if the process is
     * already killed).
     */
    public void kill() {
        if (isAlive()) {
            // This does a simple kill, which might be ignored by the running
            // process. In such situations, you should want to do something like
            // 'kill -9', but this is not easily done in Java (without doing
            // nasty hacks; see for example:
            // <http://stackoverflow.com/questions/4912282/java-tool-method-to-force-kill-a-child-process>).
            m_runningProcess.destroy();

            try {
                // We don't care for the result...
                waitForTermination();
            }
            catch (InterruptedException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Creates a new process from the contained launch configuration and starts it as a new
     * {@link Process}. After this, it waits until the process is completed.
     * 
     * @throws IllegalStateException in case there is already a running process that is not yet
     *         finished;
     * @throws IOException if an I/O error occurs during the invocation of the process.
     */
    public void run() throws IllegalStateException, IOException {
        if (isAlive()) {
            throw new IllegalStateException("Process is still running & alive!");
        }

        Properties customEnv = null;
        if (m_processLifecycleListener != null) {
            customEnv = m_processLifecycleListener.beforeProcessStart(m_launchConfiguration);
        }

        final ProcessBuilder pb = createProcessBuilder(customEnv);

        // Invoke the actual executable asynchronously...
        m_runningProcess = pb.start();

        // Make sure we don't overflow any native buffers...
        redirectProcessStreams(m_runningProcess);
    }

    /**
     * Waits until the process is terminated.
     * 
     * @return the exit value of the terminated process, or <code>null</code> if the process was
     *         never started.
     * @throws InterruptedException in case we're interrupted while waiting for the process to
     *         terminate.
     */
    public final Integer waitForTermination() throws InterruptedException {
        if (m_runningProcess != null) {
            final int result = m_runningProcess.waitFor();

            cleanup();

            return result;
        }
        return null;
    }

    /**
     * Interrupts and closes all process stream redirects.
     */
    private void closeProcessStreamRedirects() {
        // Make sure the redirectors are interrupted as well...
        if (m_processStdinRedirector != null) {
            try {
                m_processStdinRedirector.join(1000);
            }
            catch (InterruptedException exception) {
                exception.printStackTrace();
            }
            m_processStdinRedirector = null;
        }
        if (m_processStdoutRedirector != null) {
            try {
                m_processStdoutRedirector.join(1000);
            }
            catch (InterruptedException exception) {
                exception.printStackTrace();
            }
            m_processStdoutRedirector = null;
        }
    }

    /**
     * Creates the process builder and ensures all of its settings are correct to be launched.
     * 
     * @param customEnv the custom environment settings of the to-be-created process, can be
     *        <code>null</code> if no additional environment settings are desired.
     * @return a {@link ProcessBuilder} instance, never <code>null</code>.
     */
    private ProcessBuilder createProcessBuilder(Properties customEnv) {
        ProcessBuilder pb = new ProcessBuilder(m_launchConfiguration.getCommandLine());
        // Make sure we grab both stdout *and* stderr!
        pb.redirectErrorStream(true /* redirectErrorStream */);
        pb.directory(m_launchConfiguration.getWorkingDirectory());
        // We do *not* override/set a new environment for this process. This can
        // be easily done with shell scripting as well, if desired...
        if (customEnv != null) {
            Map<String, String> env = pb.environment();
            for (Object obj : customEnv.keySet()) {
                String key = (String) obj;
                env.put(key, customEnv.getProperty(key));
            }
        }
        return pb;
    }

    /**
     * Factory method for creating a {@link InputStreamRedirector} instance that redirects the stdin
     * of the given process to the contained input stream (if available).
     * 
     * @param process the {@link Process} to create the input stream redirector for, can be
     *        <code>null</code>.
     * @return an input stream redirector instance, never <code>null</code>.
     */
    private InputStreamRedirector createStdinRedirector(Process process) {
        if (m_processStreamListener != null && m_processStreamListener.wantsStdin()) {
            m_processStreamListener.setStdin(m_launchConfiguration, process.getOutputStream());
        }
        return null;
    }

    /**
     * Factory method for creating a {@link InputStreamRedirector} instance that redirects the
     * stdout of the given process to either the contained output stream, or to '/dev/null'.
     * 
     * @param process the {@link Process} to create the input stream redirector for, cannot be
     *        <code>null</code>.
     * @return an input stream redirector instance, never <code>null</code>.
     */
    private InputStreamRedirector createStdoutRedirector(Process process) {
        if (m_processStreamListener != null && m_processStreamListener.wantsStdout()) {
            m_processStreamListener.setStdout(m_launchConfiguration, process.getInputStream());

            return null;
        }
        // Redirect to /dev/null!
        return new InputStreamRedirector(process.getInputStream());
    }

    /**
     * Returns an indication whether or not the process is still alive and running, or already
     * terminated.
     * 
     * @return <code>true</code> if the process is still running, <code>false</code> otherwise.
     */
    private boolean isAlive() {
        try {
            if (m_runningProcess != null) {
                // If the process is still alive it'll throw an exception...
                m_runningProcess.exitValue();
            }

            // No longer alive...
            return false;
        }
        catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /**
     * Redirects the stdin/stdout streams of the given process.
     * 
     * @param process the process to redirect the streams for, cannot be <code>null</code>.
     */
    private void redirectProcessStreams(Process process) {
        m_processStdoutRedirector = createStdoutRedirector(process);
        if (m_processStdoutRedirector != null) {
            m_processStdoutRedirector.start();
        }
        m_processStdinRedirector = createStdinRedirector(process);
        if (m_processStdinRedirector != null) {
            m_processStdinRedirector.start();
        }
    }
}
