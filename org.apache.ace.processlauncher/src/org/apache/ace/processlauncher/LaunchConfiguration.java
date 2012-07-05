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

import java.io.File;

/**
 * Denotes a particular launch configuration for a process, describing what and how to launch.
 * <p>
 * You can create a new launch configuration by "pushing" a new configuration to the ConfigAdmin
 * service. If you use something like Felix FileInstall, you can do this by creating a new
 * properties file with the following contents:
 * </p>
 * 
 * <pre>
 * # Denotes how many instances of the process should be started, >= 0.
 * # Optional, defaults to 1 instance.
 * #instance.count = 1
 * # What working directory should the executable start in?
 * # Optional, defaults to the current working directory.
 * #executable.workingDir = /path/to/cwd
 * # The executable to start, should be the fully qualified path to the
 * # executable.
 * # Mandatory, no default.
 * executable.name = /path/to/java
 * # The arguments for the executable.
 * # Mandatory, no default.
 * executable.args = -jar /path/to/jar
 * # The OSGi-filter clause which should resolve to a (single!)
 * # ProcessStreamListener instance. When given, it will be used to provide
 * # access to the launched process' stdin/stdout streams. NOTE: if you interact
 * # with the process this way, it could be that the process only terminates
 * # when you *explicitly* close the stdin stream.
 * # Optional, defaults to an empty/no filter.
 * #executable.processStreamListener =
 * # The OSGi-filter clause that should resolve to a ProcessLifecycleListener
 * # service-instance. When given, it will be used to provide hooks to when the
 * # executable is (re)started and stopped. 
 * # Optional, defaults to an empty/no filter.
 * #executable.processLifecycleListener = 
 * # When 'true' the process will automatically be restarted when it terminates
 * # with a 'abnormal' exit value (see 'executable.normalExitValue'). Any
 * # defined process stream listener will be re-invoked with the newly started
 * # process.
 * # Optional, defaults to 'false'.
 * #executable.respawnAutomatically = false
 * # Denotes what the 'normal' exit value of the process is, so the launcher can
 * # determine when a process is terminated abnormally.
 * # Optional, defaults to 0.
 * #executable.normalExitValue = 0
 * </pre>
 */
public interface LaunchConfiguration {

    /**
     * Creates a fully qualified command line as array, in which the first element is the command to
     * execute, and the remainder of the array consists of the arguments that are passed to the
     * command.
     * 
     * @return the command line, as array, never <code>null</code>.
     */
    String[] getCommandLine();

    /**
     * Returns the optional arguments that should be passed to the executable.
     * 
     * @return the executable arguments, never <code>null</code>, but can be an empty array.
     * @see #getExecutableName()
     */
    String[] getExecutableArgs();

    /**
     * Returns the full path-name to the executable to launch.
     * 
     * @return the executable name, never <code>null</code>.
     */
    String getExecutableName();

    /**
     * Returns the number of instances that should be launched.
     * 
     * @return an instance count, >= 1.
     */
    int getInstanceCount();

    /**
     * Returns the exit value that indicates whether or not the process is terminated
     * successfully/normally. By convention, this is 0, but not all executables adhere to this
     * convention.
     * 
     * @return the normal exit value, defaults to 0.
     */
    int getNormalExitValue();

    /**
     * Returns a filter-string to obtain the process' stream listener that wants to interact with
     * the process.
     * <p>
     * Only a single process stream listener should be resolved by the returned filter condition.
     * When multiple listeners are returned, the one with the highest service-ID will be used as
     * defined in the OSGi specification.
     * </p>
     * 
     * @return a OSGi-filter condition, or <code>null</code> (the default) if no interaction with
     *         the process is desired.
     */
    String getProcessStreamListener();

    /**
     * Returns a filter-string to obtain the process' lifecycle listener that wants to get notified
     * about the lifecycle of the process.
     * <p>
     * Only a single process lifecycle listener should be resolved by the returned filter condition.
     * When multiple listeners are returned, the one with the highest service-ID will be used as
     * defined in the OSGi specification.
     * </p>
     * 
     * @return a OSGi-filter condition, or <code>null</code> (the default) if no notifications on
     *         the process lifecycle are desired.
     */
    String getProcessLifecycleListener();

    /**
     * Returns the working directory to use when launching the executable.
     * 
     * @return a working directory, as {@link File} object, or <code>null</code> if no working
     *         directory is to be set and the default should be used.
     */
    File getWorkingDirectory();

    /**
     * Returns whether the process should be respawned after it terminated with a non-zero exit
     * code.
     * 
     * @return <code>true</code> if the process should be respawned when it terminates with a
     *         non-zero exit code, <code>false</code> to leave it as-is (terminated).
     */
    boolean isRespawnAutomatically();

}
