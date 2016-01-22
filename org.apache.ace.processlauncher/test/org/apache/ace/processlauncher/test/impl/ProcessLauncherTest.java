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
import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLifecycleListener;
import org.apache.ace.processlauncher.ProcessStreamListener;
import org.apache.ace.processlauncher.impl.LaunchConfigurationImpl;
import org.apache.ace.processlauncher.impl.ProcessLauncher;
import org.apache.ace.processlauncher.util.InputStreamRedirector;
import org.testng.annotations.Test;

/**
 * Test cases for {@link ProcessLauncher}.
 */
public class ProcessLauncherTest {

    /**
     * Tests that an existing executable (Java) can be called with valid arguments and its output
     * can be read.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallAlreadyRunningProcessCausesExceptionFail() throws Exception {
        String execName = determineJavaExecutable();

        TestProcessStreamListener psl = new TestProcessStreamListener(false /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();

        try {
            launcher.run(); // should fail!
            fail("Exception expected!");
        }
        catch (IllegalStateException expected) {
            // Ok...
        }
    }

    /**
     * Tests that for a given {@link ProcessLifecycleListener} the lifecycle methods are called.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testLifecycleMethodsAreCalledOk() throws Exception {
        String execName = determineJavaExecutable();

        TestProcessStreamListener psl = new TestProcessStreamListener(false /* wantsStdout */);
        TestProcessLifecycleListener pll = new TestProcessLifecycleListener();

        ProcessLauncher launcher = createProcessLauncher(psl, pll, execName);

        launcher.run();

        assertTrue(pll.m_beforeCalled);

        // Will wait until process is finished and calls our lifecycle method...
        launcher.waitForTermination();

        assertTrue(pll.m_afterCalled);
    }

    /**
     * Tests that calling {@link ProcessLauncher#cleanup()} will cause an exception if the process
     * is not yet terminated.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallCleanupOnRunningProcessFails() throws Exception {
        String execName = determineJavaExecutable();

        TestProcessStreamListener psl = new TestProcessStreamListener(false /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();

        try {
            launcher.cleanup(); // should fail!
            fail("Exception expected!");
        }
        catch (IllegalStateException expected) {
            // Ok...
        }
    }

    /**
     * Tests that calling {@link ProcessLauncher#cleanup()} will cause no exception if the process
     * is terminated.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallCleanupOnTerminatedProcessOk() throws Exception {
        String execName = determineJavaExecutable();

        TestProcessStreamListener psl = new TestProcessStreamListener(false /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();

        sleep(500);

        launcher.cleanup();
    }

    /**
     * Tests that an existing executable (Java) can be called with valid arguments and its output
     * can be read.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallExistingExecutableWithoutArgumentOk() throws Exception {
        String execName = determineJavaExecutable();

        TestProcessStreamListener psl = new TestProcessStreamListener(true /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();
        Integer exitValue = launcher.waitForTermination();

        assertNotNull(exitValue);
        assertEquals(1, exitValue.intValue());
        // Both methods should return the same exit value!
        assertEquals(exitValue, launcher.getExitValue());

        String stdout = psl.slurpStdout();
        assertNotNull(stdout);
        assertTrue(stdout.length() > 0);

        // Make sure the test doesn't fail when the usage text is translated or
        // something...
        assertTrue(stdout.contains("Usage: java"), stdout);
    }

    /**
     * Tests that an existing executable (Java) can be called with invalid arguments and its output
     * can be read.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallExistingExecutableWithUnknownArgumentsOk() throws Exception {
        String execName = determineJavaExecutable();
        String execArgs = "-nonExistingArg";

        ProcessLauncher launcher = createProcessLauncher(execName, execArgs);

        launcher.run();
        Integer exitValue = launcher.waitForTermination();

        assertNotNull(exitValue);
        assertFalse(0 == exitValue.intValue());
        // Both methods should return the same exit value!
        assertEquals(exitValue, launcher.getExitValue());
    }

    /**
     * Tests that an existing executable (Java) can be called with valid arguments and its output
     * can be read.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallExistingExecutableWithValidArgumentOk() throws Exception {
        String execName = determineJavaExecutable();
        String execArgs = "-version";

        TestProcessStreamListener psl = new TestProcessStreamListener(true /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName, execArgs);

        launcher.run();
        Integer exitValue = launcher.waitForTermination();

        assertNotNull(exitValue);
        assertEquals(0, exitValue.intValue());

        String stdout = psl.slurpStdout();
        assertNotNull(stdout);
        assertTrue(stdout.length() > 0);

        // Make sure the test doesn't fail when the usage text is translated or
        // something...
        assertTrue(stdout.contains("java version"), stdout);
    }

    /**
     * Tests that an existing executable (Java) can be called and its output can be read.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testCallNonExistingExecutableOk() throws Exception {
        String execName = "/path/to/java";
        String execArgs = "-version";

        ProcessLauncher launcher = createProcessLauncher(execName, execArgs);

        try {
            launcher.run(); // should fail!
            fail("Exception expected!");
        }
        catch (IOException expected) {
            // Ok...
        }
    }

    /**
     * Tests that attempting to create a new {@link ProcessLauncher} without a valid launch
     * configuration yields an exception.
     */
    @Test(groups = { UNIT })
    public void testCreateProcessLauncherWithoutLaunchConfigurationFail() {
        try {
            new ProcessLauncher(null);
            fail("Exception expected!");
        }
        catch (IllegalArgumentException expected) {
            // Ok...
        }
    }

    /**
     * Tests that attempting to obtain the exit value without a launched process yields a null
     * value.
     */
    @Test(groups = { UNIT })
    public void testGetExitValueWithoutLaunchedProcessReturnsNull() {
        String execName = determineJavaExecutable();
        String execArgs = "-version";

        ProcessLauncher launcher = createProcessLauncher(execName, execArgs);

        assertNull(launcher.getExitValue());
    }

    /**
     * Tests that we can send commands to a running process and capture the results of these
     * commands.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testInteractWithProcessOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        String execName = "/bin/sh";
        String input = "echo '1'\nls $0\nsleep 1\n";

        TestProcessStreamListener psl = new TestProcessStreamListener(true /* wantsStdin */, true /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();

        psl.writeToStdin(input);
        psl.closeStdin();

        Integer exitValue = launcher.waitForTermination();

        assertNotNull(exitValue);
        assertEquals(0, exitValue.intValue());

        String stdout = psl.slurpStdout();
        assertNotNull(stdout);
        assertTrue(stdout.length() > 0);

        // Make sure the test doesn't fail when the usage text is translated or
        // something...
        assertEquals("1\n/bin/sh\n", stdout);
    }

    /**
     * Tests that we can send commands to a running process and capture the results of these
     * commands.
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testInteractWithProcessThroughArgumentsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        String execName = "/bin/sh";
        String[] execArgs = { "-c", "echo '1'\nls $0\nsleep 1\n" };

        TestProcessStreamListener psl = new TestProcessStreamListener(false /* wantsStdin */, true /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName, execArgs);

        launcher.run();

        Integer exitValue = launcher.waitForTermination();

        String stdout = psl.slurpStdout();
        assertNotNull(stdout);
        assertTrue(stdout.length() > 0);

        assertNotNull(exitValue);
        assertEquals(0, exitValue.intValue());

        assertEquals("1\n/bin/sh\n", stdout);
    }

    /**
     * Tests that we can send commands to a running cat-process and capture the results of these
     * commands.
     * <p>
     * The cat-command is a somewhat "nasty" command as it won't exit until its input stream is
     * properly closed.
     * </p>
     * 
     * @throws Exception not part of this test case.
     */
    @Test(groups = { UNIT })
    public void testProcessStdinIsProperlyClosedOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        String execName = "/bin/cat";
        String input = "echo '1'\nls $0\nqux qoo\n";

        TestProcessStreamListener psl = new TestProcessStreamListener(true /* wantsStdin */, true /* wantsStdout */);

        ProcessLauncher launcher = createProcessLauncher(psl, null, execName);

        launcher.run();

        // Issue the command...
        psl.writeToStdin(input);

        sleep(1000);

        psl.closeStdin();

        Integer exitValue = launcher.waitForTermination();

        assertNotNull(exitValue);
        assertEquals(0, exitValue.intValue());

        String stdout = psl.slurpStdout();
        assertNotNull(stdout);
        assertTrue(stdout.length() > 0);

        // We should get the exact same output as we've put into the command...
        assertEquals(input, stdout);
    }

    /**
     * Creates a new launch configuration for the given executable and arguments.
     * 
     * @param captureProcessOutput <code>true</code> if the process output is to be captured,
     *        <code>false</code> otherwise;
     * @param execName the name of the executable;
     * @param execArgs the (optional) arguments.
     * @return a {@link LaunchConfigurationImpl} instance, never <code>null</code>.
     */
    private LaunchConfiguration createLaunchConfiguration(boolean respawnAutomatically, String execName,
        String... execArgs) {
        return new LaunchConfigurationImpl(1, execName, execArgs, null, respawnAutomatically);
    }

    /**
     * Creates a new process launcher instance for the given executable and arguments.
     * 
     * @param execName the name of the executable;
     * @param execArgs the (optional) arguments.
     * @return a {@link ProcessLauncher} instance, never <code>null</code>.
     */
    private ProcessLauncher createProcessLauncher(ProcessStreamListener processStreamListener,
        ProcessLifecycleListener processLifecycleListener, String execName, String... execArgs) {
        return new ProcessLauncher(createLaunchConfiguration(false, execName, execArgs), processStreamListener,
            processLifecycleListener);
    }

    /**
     * Creates a new process launcher instance for the given executable and arguments.
     * 
     * @param execName the name of the executable;
     * @param execArgs the (optional) arguments.
     * @return a {@link ProcessLauncher} instance, never <code>null</code>.
     */
    private ProcessLauncher createProcessLauncher(String execName, String... execArgs) {
        return new ProcessLauncher(createLaunchConfiguration(false, execName, execArgs));
    }

    /**
     * Returns the full path to the java executable (the one that is used to invoke this test).
     * 
     * @return a full path to the executable, never <code>null</code>.
     */
    private String determineJavaExecutable() {
        StringBuilder sb = new StringBuilder(System.getProperty("java.home"));
        sb.append(File.separatorChar).append("bin").append(File.separatorChar).append("java");
        return sb.toString();
    }

    /**
     * Test implementation of {@link ProcessLifecycleListener}.
     * 
     * @author jwjanssen
     */
    static final class TestProcessLifecycleListener implements ProcessLifecycleListener {
        private volatile boolean m_beforeCalled;
        private volatile boolean m_afterCalled;

        /**
         * {@inheritDoc}
         */
        public void afterProcessEnd(LaunchConfiguration configuration) {
            m_afterCalled = true;
        }

        /**
         * {@inheritDoc}
         */
        public Properties beforeProcessStart(LaunchConfiguration configuration) {
            m_beforeCalled = true;
            return null;
        }
    }

    /**
     * Provides a mock implementation of {@link ProcessStreamListener} that is used in the various
     * test cases.
     * 
     * @author jwjanssen
     */
    static final class TestProcessStreamListener implements ProcessStreamListener {
        private final boolean m_wantsStdin;
        private final boolean m_wantsStdout;

        private OutputStream m_stdin;
        private OutputStream m_stdout;

        public TestProcessStreamListener(boolean wantsStdout) {
            this(false /* wantsStdin */, wantsStdout);
        }

        public TestProcessStreamListener(boolean wantsStdin, boolean wantsStdout) {
            m_wantsStdin = wantsStdin;
            m_wantsStdout = wantsStdout;
        }

        public synchronized void closeStdin() throws IOException {
            m_stdin.flush();
            m_stdin.close();
        }

        public void setStdin(LaunchConfiguration launchConfiguration, OutputStream outputStream) {
            m_stdin = outputStream;
        }

        public void setStdout(LaunchConfiguration launchConfiguration, InputStream inputStream) {
            m_stdout = new ByteArrayOutputStream(1024);
            InputStreamRedirector isr = new InputStreamRedirector(inputStream, m_stdout);
            isr.start();
        }

        public String slurpStdout() throws IOException {
            assertNotNull(m_stdout);
            m_stdout.flush();
            return m_stdout.toString();
        }

        public boolean wantsStdin() {
            return m_wantsStdin;
        }

        public boolean wantsStdout() {
            return m_wantsStdout;
        }

        public void writeToStdin(String commands) throws IOException {
            assertNotNull(m_stdin);
            m_stdin.write(commands.getBytes());
        }
    }
}
