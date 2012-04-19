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

import java.util.Dictionary;
import java.util.Enumeration;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.ConfigurationException;

/**
 * Provides a factory for creating new {@link LaunchConfiguration}s.
 */
public abstract class LaunchConfigurationFactory {
    /** Denotes the number of instances to start (defaults to 1). */
    public static final String INSTANCE_COUNT = "instance.count";
    /** Denotes the working directory to start from (defaults to the current working directory). */
    public static final String WORKING_DIRECTORY = "executable.workingDir";
    /** Denotes the executable to start (fully qualified pathname). */
    public static final String EXECUTABLE_NAME = "executable.name";
    /** Denotes the executable arguments (optional). */
    public static final String EXECUTABLE_ARGS = "executable.args";
    /** Denotes the filter-clause to obtain the process stream listener (optional). */
    public static final String PROCESS_STREAM_LISTENER_FILTER = "executable.processStreamListener";
    /** Denotes the filter-clause to obtain the process lifecycle listener (optional). */
    public static final String PROCESS_LIFECYCLE_LISTENER_FILTER = "executable.processLifecycleListener";
    /**
     * Denotes whether or not the executable should be restarted upon unexpected termination
     * (defaults to false).
     */
    public static final String RESPAWN_AUTOMATICALLY = "executable.respawnAutomatically";
    /** Denotes the exit value that is to be considered normal (defaults to 0). */
    public static final String NORMAL_EXIT_VALUE = "executable.normalExitValue";

    /** The minimal instance count; less than one has no sense. */
    private static final int MINIMAL_INSTANCE_COUNT = 1;

    /**
     * Creates a new {@link LaunchConfigurationFactory} instance, never used.
     */
    private LaunchConfigurationFactory() {
        // No-op
    }

    /**
     * Creates a new {@link LaunchConfiguration} instance.
     * 
     * @param config the configuration to create a {@link LaunchConfiguration} for, cannot be
     *        <code>null</code>.
     * @return a new {@link LaunchConfiguration} instance based on the information in the given
     *         configuration, never <code>null</code>.
     * @throws ConfigurationException in case the given configuration contains invalid keys and/or
     *         values.
     */
    public static LaunchConfiguration create(final Dictionary<Object, Object> config) throws ConfigurationException {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null!");
        }

        // Sanity check; make sure all mandatory properties are available...
        checkMandatoryProperties(config, EXECUTABLE_ARGS, EXECUTABLE_NAME);

        int instanceCount = 1;
        String workingDirectory = null;
        String executableName = null;
        String[] executableArgs = null;
        int normalExitValue = 0;
        String processStreamListenerFilter = null;
        String processLifecycleListenerFilter = null;
        boolean respawnAutomatically = false;

        Enumeration<Object> keys = config.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            String value = String.valueOf(config.get(key)).trim();

            if (INSTANCE_COUNT.equals(key)) {
                instanceCount = parseInstanceCount(value);
            }
            else if (WORKING_DIRECTORY.equals(key)) {
                workingDirectory = value.isEmpty() ? null : value;
            }
            else if (EXECUTABLE_NAME.equals(key)) {
                executableName = parseExecutableName(value);
            }
            else if (EXECUTABLE_ARGS.equals(key)) {
                executableArgs = parseExecutableArguments(value);
            }
            else if (PROCESS_STREAM_LISTENER_FILTER.equals(key)) {
                processStreamListenerFilter = parseFilter(value);
            }
            else if (PROCESS_LIFECYCLE_LISTENER_FILTER.equals(key)) {
                processLifecycleListenerFilter = parseFilter(value);
            }
            else if (RESPAWN_AUTOMATICALLY.equals(key)) {
                respawnAutomatically = Boolean.parseBoolean(value);
            }
            else if (NORMAL_EXIT_VALUE.equals(key)) {
                normalExitValue = parseExitValue(value);
            }
        }

        return new LaunchConfigurationImpl(instanceCount, workingDirectory, executableName, executableArgs,
            normalExitValue, processStreamListenerFilter, processLifecycleListenerFilter, respawnAutomatically);
    }

    /**
     * Tests whether all mandatory properties are available in a given configuration.
     * 
     * @param config the configuration to test for mandatory keys;
     * @param mandatoryKeys the keys to check.
     * @throws ConfigurationException in case one of the given keys is missing from the given
     *         configuration.
     */
    private static void checkMandatoryProperties(final Dictionary<Object, Object> config, final String... mandatoryKeys)
        throws ConfigurationException {
        for (String key : mandatoryKeys) {
            if (config.get(key) == null) {
                throw new ConfigurationException(key, "Missing configuration property: " + key);
            }
        }
    }

    /**
     * Parses the given value and splits it into a string array, taking care of quoted strings.
     * 
     * @param value the value to split to a string array.
     * @return a string array, never <code>null</code>, but can be empty if the given value was
     *         <code>null</code> or empty.
     */
    private static String[] parseExecutableArguments(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return new String[0];
        }
        return StringSplitter.split(value);
    }

    /**
     * Parses the given executable name to something sensible.
     * 
     * @param value the value to parse as executable name.
     * @return the executable name, never <code>null</code>.
     * @throws ConfigurationException in case the given value was <code>null</code> or empty.
     */
    private static String parseExecutableName(final String value) throws ConfigurationException {
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException(EXECUTABLE_NAME, "Invalid executable name!");
        }
        return value;
    }

    /**
     * Parses the given string value as integer exit value.
     * 
     * @param value the string to parse as exit value, can be <code>null</code>.
     * @return the integer representation of the given string.
     * @throws ConfigurationException in case the given value is not an integer value, or is an
     *         invalid value for instance counts.
     */
    private static int parseExitValue(String value) throws ConfigurationException {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception) {
            throw new ConfigurationException(NORMAL_EXIT_VALUE, "Invalid exit value!");
        }
    }

    /**
     * Parses the given value as OSGi {@link Filter}.
     * 
     * @param value the value to parse as filter, cannot be <code>null</code>.
     * @return the given input value, if it is a valid filter condition.
     * @throws ConfigurationException in case the given value consists of an invalid filter.
     */
    private static String parseFilter(String value) throws ConfigurationException {
        try {
            FrameworkUtil.createFilter(value);
            return value;
        }
        catch (InvalidSyntaxException exception) {
            throw new ConfigurationException(PROCESS_STREAM_LISTENER_FILTER, "Invalid filter syntax! Reason: "
                + exception.getMessage());
        }
    }

    /**
     * Parses a given string value into a numeric instance count.
     * 
     * @param value the string to parse as instance count, can be <code>null</code>.
     * @return the instance count.
     * @throws ConfigurationException in case the given value is not an integer value, or is an
     *         invalid value for instance counts.
     */
    private static int parseInstanceCount(final String value) throws ConfigurationException {
        int instanceCount = -1;
        try {
            instanceCount = Integer.parseInt(value);
        }
        catch (NumberFormatException exception) {
            // Ignore, will be picked up below...
        }

        if (instanceCount < MINIMAL_INSTANCE_COUNT) {
            throw new ConfigurationException(INSTANCE_COUNT, "Invalid instance count!");
        }
        return instanceCount;
    }
}
