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
package org.apache.ace.processlauncher;

import java.util.Properties;

import aQute.bnd.annotation.ConsumerType;

/**
 * Allows code to be run <em>before</em> a process is actually launched, and <em>after</em> a
 * process is terminated.
 * <p>
 * A typical use case for this would be that you might want to set up some process-specific
 * directories and/or configuration files for each individually launched process.
 * </p>
 */
@ConsumerType
public interface ProcessLifecycleListener {

    /**
     * Called right before the process denoted by the given launch configuration is started.
     * <p>
     * Use this method to set up directories, or pre-process (provisioned) data/configuration files
     * or other actions that need to be done in order to get the process properly up and running.
     * </p>
     * <p>
     * This method can also adjust the environment of the to-be-created process by returning a
     * populated {@link Properties} object.
     * </p>
     * 
     * @param configuration the launch configuration of the process that is about to start, never
     *        <code>null</code>.
     * @return the environment properties of the to-be-created process. Can be <code>null</code> if
     *         no additional environment settings are wanted/desired.
     */
    Properties beforeProcessStart(LaunchConfiguration configuration);

    /**
     * Called right after the process denoted by the given launch configuration is terminated.
     * <p>
     * Use this method to clean up directories.
     * </p>
     * 
     * @param configuration the launch configuration of the process that is just terminated, never
     *        <code>null</code>.
     */
    void afterProcessEnd(LaunchConfiguration configuration);
}
