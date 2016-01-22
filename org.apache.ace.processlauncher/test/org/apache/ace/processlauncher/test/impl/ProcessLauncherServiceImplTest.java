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

import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.processlauncher.impl.LaunchConfigurationFactory;
import org.apache.ace.processlauncher.impl.ProcessLauncherServiceImpl;
import org.apache.ace.processlauncher.impl.ProcessManager;
import org.apache.ace.processlauncher.impl.ProcessManagerImpl;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link ProcessLauncherServiceImpl}.
 */
public class ProcessLauncherServiceImplTest {

    private ProcessLauncherServiceImpl m_service;
    private Dictionary<Object, Object> m_launchConfig;

    /**
     * Tests that adding/inserting a new launch configuration causes a configuration entry to be
     * created.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testAddConfigurationWorksOk() throws ConfigurationException {
        final String pid = "existing-pid";

        assertEquals(0, m_service.getLaunchConfigurationCount());

        m_service.updated(pid, m_launchConfig);
        assertEquals(1, m_service.getLaunchConfigurationCount());
    }

    /**
     * Tests that deleting an existing launch configuration works & doesn't cause an exception.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testDeleteExistingConfigurationOk() throws ConfigurationException {
        final String pid = "existing-pid";

        m_service.updated(pid, m_launchConfig);
        assertTrue(m_service.containsPid(pid));

        m_service.deleted(pid);
        assertFalse(m_service.containsPid(pid));
    }

    /**
     * Tests that deleting a non-existing launch configuration doesn't cause an exception.
     */
    @Test(groups = { UNIT })
    public void testDeleteNonExistingConfigurationOk() {
        m_service.deleted("non-existing-pid");
    }

    /**
     * Tests that the getName method doesn't return <code>null</code>.
     */
    @Test(groups = { UNIT })
    public void testGetNameOk() {
        assertNotNull(m_service.getName());
    }

    /**
     * Tests the running process count is obtained from the (mocked) process manager.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testGetRunningProcessCountOk() throws Exception {
        final String pid = "existing-pid";

        m_service.updated(pid, m_launchConfig);
        assertEquals(2, m_service.getRunningProcessCount());
    }

    /**
     * Tests that updating an existing launch configuration cause the original configuration entry
     * to be updated.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testReplacingConfigurationWorksOk() throws ConfigurationException {
        final String pid = "existing-pid";

        assertEquals(0, m_service.getLaunchConfigurationCount());

        m_service.updated(pid, m_launchConfig);
        assertEquals(1, m_service.getLaunchConfigurationCount());

        m_service.updated(pid, m_launchConfig);
        assertEquals(1, m_service.getLaunchConfigurationCount());
    }

    /**
     * Tests that all existing launch configurations are removed when the service shutdown method is
     * called.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testShutdownRemovesAllConfigurationsOk() throws Exception {
        assertEquals(0, m_service.getLaunchConfigurationCount());

        m_service.updated("pid1", m_launchConfig);
        m_service.updated("pid2", m_launchConfig);

        assertEquals(2, m_service.getLaunchConfigurationCount());

        m_service.shutdown();

        assertEquals(0, m_service.getLaunchConfigurationCount());
    }

    /**
     * Tests that updating an existing launch configuration cause the original configuration entry
     * to be updated.
     * 
     * @throws ConfigurationException not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testUpdateConfigurationWorksOk() throws ConfigurationException {
        final String pid = "existing-pid";

        assertEquals(0, m_service.getLaunchConfigurationCount());

        m_service.updated(pid, null);
        assertEquals(1, m_service.getLaunchConfigurationCount());

        m_service.updated(pid, m_launchConfig);
        assertEquals(1, m_service.getLaunchConfigurationCount());
    }

    /**
     * Set up for each test case.
     * 
     * @throws Exception not part of this test case.
     */
    @BeforeMethod
    protected void setUp() throws Exception {
        m_service = new ProcessLauncherServiceImpl();
        m_service.setLogger(TestUtils.createNullObject(LogService.class));

        ProcessManager processManager = mock(ProcessManagerImpl.class);
        when(processManager.getRunningProcessesCount()).thenReturn(2);
        m_service.setProcessManager(processManager);

        m_launchConfig = new Properties();
        m_launchConfig.put(LaunchConfigurationFactory.INSTANCE_COUNT, "1");
        m_launchConfig.put(LaunchConfigurationFactory.EXECUTABLE_NAME, "/path/to/foo");
        m_launchConfig.put(LaunchConfigurationFactory.EXECUTABLE_ARGS, "");
        m_launchConfig.put(LaunchConfigurationFactory.PROCESS_STREAM_LISTENER_FILTER, "(foo=bar)");
        m_launchConfig.put(LaunchConfigurationFactory.RESPAWN_AUTOMATICALLY, "false");
        m_launchConfig.put(LaunchConfigurationFactory.NORMAL_EXIT_VALUE, "0");
    }
}
