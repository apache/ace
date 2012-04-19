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

import static org.apache.ace.processlauncher.test.impl.TestUtil.getOSName;
import static org.apache.ace.processlauncher.test.impl.TestUtil.sleep;

import java.io.File;

import junit.framework.TestCase;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.impl.LaunchConfigurationImpl;
import org.apache.ace.processlauncher.impl.ProcessManager;
import org.apache.ace.processlauncher.impl.ProcessManagerImpl;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;

/**
 * Test cases for {@link ProcessManager}.
 */
public class ProcessManagerImplTest extends TestCase {

    private ProcessManagerImpl m_processManager;

    /**
     * Tests that launching a simple process works for a UNIX-derived operating system.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessOnUnixDerivativeOk() throws Exception {
        // This test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("/bin/sh", "-c", "sleep 1 && exit 1");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        m_processManager.launch("myPid", launchConfig);

        // make sure we sleep a little to ensure the process is started
        sleep(100);

        int processCountAfter = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfter > processCountBefore);

        assertEquals(processCountAfter, m_processManager.getRunningProcessesCount());

        // make sure we sleep a little longer to allow the process to finish...
        sleep(1000);

        assertEquals(processCountBefore, m_processManager.getRunningProcessesCount());
    }

    /**
     * Tests that launching a simple process works for a Windows operating system.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessOnWindowsOk() throws Exception {
        // This test will only work on Windows!
        if (!getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("PING", "-n", "1", "127.0.0.1");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        m_processManager.launch("myPid", launchConfig);

        // make sure we sleep a little to ensure the process is started
        sleep(100);

        int processCountAfter = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfter > processCountBefore);

        assertEquals(processCountAfter, m_processManager.getRunningProcessesCount());

        // make sure we sleep a little longer to allow the process to finish...
        sleep(1000);

        assertEquals(processCountBefore, m_processManager.getRunningProcessesCount());
    }

    /**
     * Tests that respawning a simple process works for a UNIX-derived operating system.
     * 
     * @throws Exception not part of this test case.
     */
    public void testRespawnProcessOnUnixDerivativeOk() throws Exception {
        // This test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        int count = 2;
        File tmpFile = File.createTempFile("ace", null);
        String tmpFilename = tmpFile.getAbsolutePath();

        // Seems daunting, but what this command does is simply count the number
        // of lines in a given file (= tmpFile), and append this to that same
        // file; it uses this number to create an exit value, which counts from
        // <count> back to 0. This way, we can test whether the respawn
        // functionality works, as this currently checks for certain exit
        // values...
        String script =
            String.format("L=$(cat %1$s | wc -l)" + "&& echo $L >> %1$s && exit $((%2$d-$L))", tmpFilename, count);

        LaunchConfiguration launchConfig =
            createLaunchConfiguration(true /* respawnAutomatically */, "/bin/bash", "-c", script);

        int processCountBefore = m_processManager.getRunningProcessesCount();

        m_processManager.launch("myPid", launchConfig);

        // sleep a little to ensure the process is started...
        sleep(10);

        assertEquals(processCountBefore + 1, m_processManager.getRunningProcessesCount());

        // make sure we sleep a little longer to allow the process to finish...
        sleep(500);

        assertEquals(processCountBefore, m_processManager.getRunningProcessesCount());

        String testResult = TestUtil.slurpFile(tmpFile);
        assertTrue(testResult, testResult.matches("(?s)^0\n1\n2\n$"));
    }

    /**
     * Tests that terminating all running processes works.
     * 
     * @throws Exception not part of this test case.
     */
    public void testShutdownOnUnixDerivativeOk() throws Exception {
        // This test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("/bin/sh", "-c", "sleep 10");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        for (int i = 0; i < 5; i++) {
            m_processManager.launch(String.format("myPid%d", i), launchConfig);
        }

        // make sure we sleep a little to ensure the processes are started
        sleep(100);

        int processCountAfter = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfter > processCountBefore);

        // Shut down the process manager; should terminate all running
        // processes...
        m_processManager.shutdown();

        assertEquals(processCountBefore, m_processManager.getRunningProcessesCount());
    }

    /**
     * Tests that terminating all running processes works.
     * 
     * @throws Exception not part of this test case.
     */
    public void testShutdownOnWindowsOk() throws Exception {
        // This test will only work on Windows!
        if (!getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("PING", "-n", "11", "127.0.0.1");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        for (int i = 0; i < 5; i++) {
            m_processManager.launch(String.format("myPid%d", i), launchConfig);
        }

        // make sure we sleep a little to ensure the processes are started
        sleep(100);

        int processCountAfter = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfter > processCountBefore);

        // Shut down the process manager; should terminate all running
        // processes...
        m_processManager.shutdown();

        assertEquals(processCountBefore, m_processManager.getRunningProcessesCount());
    }

    /**
     * Tests that terminating a single process works.
     * 
     * @throws Exception not part of this test case.
     */
    public void testTerminateProcessOnUnixDerivativeOk() throws Exception {
        // This test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("/bin/sh", "-c", "sleep 10");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        final String pid = "myPid";
        m_processManager.launch(pid, launchConfig);

        // make sure we sleep a little to ensure the process is started
        sleep(100);

        int processCountAfterLaunch = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfterLaunch > processCountBefore);

        m_processManager.terminate(pid);

        int processCountAfterTerminate = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfterTerminate == processCountBefore);
    }

    /**
     * Tests that terminating a single process works.
     * 
     * @throws Exception not part of this test case.
     */
    public void testTerminateProcessOnWindowsOk() throws Exception {
        // This test will not work on Windows!
        if (!getOSName().contains("windows")) {
            return;
        }

        LaunchConfiguration launchConfig = createLaunchConfiguration("PING", "-n", "11", "127.0.0.1");

        int processCountBefore = m_processManager.getRunningProcessesCount();

        final String pid = "myPid";
        m_processManager.launch(pid, launchConfig);

        // make sure we sleep a little to ensure the process is started
        sleep(100);

        int processCountAfterLaunch = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfterLaunch > processCountBefore);

        m_processManager.terminate(pid);

        int processCountAfterTerminate = m_processManager.getRunningProcessesCount();

        assertTrue(processCountAfterTerminate == processCountBefore);
    }

    /**
     * Set up for this test case.
     */
    @Override
    protected void setUp() {
        m_processManager = new ProcessManagerImpl();
        TestUtils.configureObject(m_processManager, LogService.class);
    }

    /**
     * Creates a new launch configuration for the given executable and arguments.
     * 
     * @param respawnAutomatically <code>true</code> if the process should be respawned
     *        automatically upon a non-zero exit value;
     * @param execName the name of the executable;
     * @param execArgs the (optional) arguments.
     * 
     * @return a {@link LaunchConfigurationImpl} instance, never <code>null</code>.
     */
    private LaunchConfiguration createLaunchConfiguration(boolean respawnAutomatically, String execName,
        String... execArgs) {
        return new LaunchConfigurationImpl(1, "/tmp", execName, execArgs, 0, null /* processStreamListenerFilter */,
            null /* processLifecycleListenerFilter */, respawnAutomatically);
    }

    /**
     * Creates a new launch configuration for the given executable and arguments.
     * 
     * @param execName the name of the executable;
     * @param execArgs the (optional) arguments.
     * 
     * @return a {@link LaunchConfigurationImpl} instance, never <code>null</code>.
     */
    private LaunchConfiguration createLaunchConfiguration(String execName, String... execArgs) {
        return createLaunchConfiguration(false /* respawnAutomatically */, execName, execArgs);
    }
}
