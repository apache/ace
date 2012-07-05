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
package org.apache.ace.processlauncher.test.impl;

import junit.framework.TestCase;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.impl.LaunchConfigurationImpl;

/**
 * Test cases for {@link LaunchConfigurationImpl}.
 */
public final class LaunchConfigurationTest extends TestCase {

    /**
     * Test that creating a valid {@link LaunchConfigurationImpl} works.
     */
    public void testCreateLaunchConfigurationOk() {
        LaunchConfiguration launchConfig = new LaunchConfigurationImpl(1, "/path/to/foo", new String[0], null, false);
        assertNotNull(launchConfig);
    }

    /**
     * Test that the {@link LaunchConfigurationImpl} constructor validates the executable name
     * properly.
     */
    public void testCreateLaunchConfigurationWithEmptyExecutableNameFail() {
        try {
            new LaunchConfigurationImpl(1, "", new String[0], null, false);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Test that the {@link LaunchConfigurationImpl} constructor validates the instance count
     * properly.
     */
    public void testCreateLaunchConfigurationWithNegativeInstanceCountFail() {
        try {
            new LaunchConfigurationImpl(-1, "/path/to/foo", new String[0], null, false);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Test that the {@link LaunchConfigurationImpl} constructor validates the executable arguments
     * properly.
     */
    public void testCreateLaunchConfigurationWithNullExecutableArgsFail() {
        try {
            new LaunchConfigurationImpl(1, "/path/to/foo", null, null, false);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Test that the {@link LaunchConfigurationImpl} constructor validates the executable name
     * properly.
     */
    public void testCreateLaunchConfigurationWithNullExecutableNameFail() {
        try {
            new LaunchConfigurationImpl(1, null, new String[0], null, false);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Test that the {@link LaunchConfigurationImpl} constructor validates the instance count
     * properly.
     */
    public void testCreateLaunchConfigurationWithZeroInstanceCountFail() {
        try {
            new LaunchConfigurationImpl(0, "/path/to/foo", new String[0], null, false);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Test that the {@link LaunchConfigurationImpl#getCommandLine()} method works properly when one
     * executable arguments are given.
     */
    public void testGetCommandLineWithOneArgumentsOk() {
        LaunchConfiguration launchConfig =
            new LaunchConfigurationImpl(1, "/path/to/foo", new String[] { "-bar" }, null, false);

        String[] commandLine = launchConfig.getCommandLine();
        assertNotNull(commandLine);
        assertEquals(2, commandLine.length);

        assertEquals("/path/to/foo", commandLine[0]);
        assertEquals("-bar", commandLine[1]);
    }

    /**
     * Test that the {@link LaunchConfigurationImpl#getCommandLine()} method works properly when no
     * executable arguments are given.
     */
    public void testGetCommandLineWithoutArgumentsOk() {
        LaunchConfiguration launchConfig =
            new LaunchConfigurationImpl(1, "cwd", "/path/to/foo", new String[0], 0, "(foo=bar)", "(qux=quu)", false);

        String[] commandLine = launchConfig.getCommandLine();
        assertNotNull(commandLine);
        assertEquals(1, commandLine.length);

        assertEquals("/path/to/foo", commandLine[0]);
        assertEquals("cwd", launchConfig.getWorkingDirectory().getName());
        assertNotNull(launchConfig.getProcessStreamListener());
        assertFalse(launchConfig.isRespawnAutomatically());
    }

    /**
     * Test that the {@link LaunchConfigurationImpl#getCommandLine()} method works properly when two
     * executable arguments are given.
     */
    public void testGetCommandLineWithTwoArgumentsOk() {
        LaunchConfiguration launchConfig =
            new LaunchConfigurationImpl(1, "/path/to/foo", new String[] { "-qux", "-bar" }, null, true);

        String[] commandLine = launchConfig.getCommandLine();
        assertNotNull(commandLine);
        assertEquals(3, commandLine.length);

        assertEquals("/path/to/foo", commandLine[0]);
        assertEquals("-qux", commandLine[1]);
        assertEquals("-bar", commandLine[2]);
        assertNull(launchConfig.getProcessStreamListener());
        assertTrue(launchConfig.isRespawnAutomatically());
    }
}
