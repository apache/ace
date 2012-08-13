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

import java.util.Properties;

import junit.framework.TestCase;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.impl.LaunchConfigurationFactory;
import org.osgi.service.cm.ConfigurationException;

/**
 * Test cases for {@link LaunchConfigurationFactory}.
 */
public final class LaunchConfigurationFactoryTest extends TestCase {

    private static void assertArrayEquals(Object[] expected, Object[] actual) {
        assertEquals("Array length mismatch!", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Array element (" + i + ") mismatch!", expected[i], actual[i]);
        }
    }

    /**
     * Tests whether creating a launch configuration based on a properties file works correctly.
     * 
     * @throws Exception not part of this test case.
     */
    public void testCreateLaunchConfigurationBasedOnPropertiesFileOk() throws Exception {
        Properties props = TestUtil.getProperties("launch.properties");
        assertNotNull(props);

        LaunchConfiguration config = LaunchConfigurationFactory.create(props);
        assertNotNull(config);

        assertEquals(2, config.getInstanceCount());
        assertEquals("/bin/sh", config.getExecutableName());
        assertArrayEquals(new String[] { "-c", "'sleep 1 && exit'", "-c", "'echo \"foo bar!\n\"'" },
            config.getExecutableArgs());
        assertNull(config.getProcessStreamListener());
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    public void testCreateWithCompleteConfigOk() throws ConfigurationException {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "(foo=bar)");
        props.put(LaunchConfigurationFactory.RESPAWN_AUTOMATICALLY, "false");
        props.put(LaunchConfigurationFactory.NORMAL_EXIT_VALUE, "2");

        LaunchConfiguration config = LaunchConfigurationFactory.create(props);
        assertNotNull(config);

        assertEquals(1, config.getInstanceCount());
        assertEquals("/path/to/foo", config.getExecutableName());
        assertArrayEquals(new String[0], config.getExecutableArgs());
        assertEquals(2, config.getNormalExitValue());
        assertNotNull(config.getProcessStreamListener());
        assertFalse(config.isRespawnAutomatically());
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithEmptyExecutableNameFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithInvalidInstanceCountFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "0");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    public void testCreateWithInvalidProcessStreamListenerFilterFail() throws ConfigurationException {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "aap"); // <=
// incorrect!
        props.put(LaunchConfigurationFactory.RESPAWN_AUTOMATICALLY, "false");
        props.put(LaunchConfigurationFactory.NORMAL_EXIT_VALUE, "2");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException exception) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithNonNumericExitValueFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");
        props.put(LaunchConfigurationFactory.NORMAL_EXIT_VALUE, "foo");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithNonNumericInstanceCountFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }

    /**
     * Tests that a null configuration cannot be given to this factory.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    public void testCreateWithNullConfigFail() throws ConfigurationException {
        try {
            LaunchConfigurationFactory.create(null);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithNullExecutableArgumentsFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithNullExecutableNameFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException e) {
            // Ok...
        }
    }

    /**
     * Tests that an incomplete configuration causes an exception.
     */
    public void testCreateWithNullInstanceCountFail() {
        Properties props = new Properties();
        props.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        props.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        props.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "true");

        try {
            LaunchConfigurationFactory.create(props);
            fail("Exception expected!");
        }
        catch (ConfigurationException expected) {
            // Ok...
        }
    }
}
