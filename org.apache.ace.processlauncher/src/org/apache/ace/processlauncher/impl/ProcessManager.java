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

import org.apache.ace.processlauncher.LaunchConfiguration;

/**
 * Provides a process manager service, which is able to launch a process (or multiple processes).
 */
public interface ProcessManager {

    /**
     * Returns the number of currently running processes.
     * <p>
     * As a side effect, all completed processes are removed from the internal administration.
     * </p>
     * 
     * @return a running process count.
     * @throws IOException in case of I/O problems.
     */
    int getRunningProcessesCount() throws IOException;

    /**
     * Launches a new process.
     * 
     * @param pid the PID of the process to launch, used for bookkeeping;
     * @param launchConfiguration the launch configuration to use for the process to be launched.
     * @throws IOException in case of I/O problems during the launch of the process.
     */
    void launch(final String pid, final LaunchConfiguration launchConfiguration) throws IOException;

    /**
     * Terminates all managed processes and shuts down this process manager.
     * 
     * @throws IOException in case of I/O problems during the launch of the process.
     */
    void shutdown() throws IOException;

    /**
     * Terminates a running process.
     * 
     * @param pid the PID of the process to terminate, used for bookkeeping.
     * @throws IOException in case of I/O problems during the launch of the process.
     */
    void terminate(final String pid) throws IOException;

}
