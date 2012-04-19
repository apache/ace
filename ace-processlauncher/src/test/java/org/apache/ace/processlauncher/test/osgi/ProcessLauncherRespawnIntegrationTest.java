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
package org.apache.ace.processlauncher.test.osgi;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.apache.ace.processlauncher.test.impl.TestUtil.getOSName;
import static org.apache.ace.processlauncher.test.impl.TestUtil.sleep;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import javax.inject.Inject;

import junit.framework.AssertionFailedError;

import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLauncherService;
import org.apache.ace.processlauncher.ProcessLifecycleListener;
import org.apache.ace.processlauncher.ProcessStreamListener;
import org.apache.ace.processlauncher.impl.ProcessLauncherServiceImpl;
import org.apache.ace.processlauncher.test.impl.TestUtil;
import org.apache.felix.dm.DependencyManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Integration test for {@link ProcessLauncherService}.
 */
@RunWith(JUnit4TestRunner.class)
public class ProcessLauncherRespawnIntegrationTest {

    @Inject
    private BundleContext m_context;
    private DependencyManager m_dependencyManager;

    /**
     * @return the PAX-exam configuration, never <code>null</code>.
     */
    @Configuration
    public Option[] config() {
        // Craft the correct options for PAX-URL wrap: to use Bnd and make a correct bundle...
        String bndOptions =
            String.format("Bundle-Activator=%1$s.osgi.Activator&" + "Export-Package=%1$s,%1$s.util&"
                + "Private-Package=%1$s.impl,%1$s.osgi", ProcessLauncherService.class.getPackage().getName());

        return options(cleanCaches(),
            junitBundles(),
            provision(mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.log").version("1.0.1")), //
            provision(mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.dependencymanager")
                .version("3.0.0")), //
            provision(mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.configadmin")
                .version("1.2.8")), //
            provision("wrap:assembly:./target/classes$" + bndOptions) //
        );
    }

    /**
     * Common set up for each test case.
     */
    @Before
    public void setUp() {
        m_dependencyManager = new DependencyManager(m_context);
    }

    /**
     * Tests that a new process will be respawned if its exit value is non-equal to two.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRespawnProcessWithExitValueTwoOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        File tmpFile = createEmptyTempFile();

        doTestRespawnProcess(tmpFile, "2", null /* psFilter */, null /* lcFilter */);

        String contents = TestUtil.slurpFile(tmpFile);
        assertTrue(contents, contents.matches("(?s)0.+1.+2.+"));
    }

    /**
     * Tests that a new process will be respawned if its exit value is non-zero.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRespawnProcessWithExitValueZeroOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        File tmpFile = createEmptyTempFile();

        doTestRespawnProcess(tmpFile, "0", null /* psFilter */, null /* lcFilter */);

        String contents = TestUtil.slurpFile(tmpFile);
        assertTrue(contents, contents.matches("(?s)0.+1.+2.+3.+4.+"));
    }

    /**
     * Tests that a new process will be respawned, and its process stream listener is called for
     * each respawn.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRespawnProcessWithProcessStreamListenersOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        File tmpFile = createEmptyTempFile();

        TestProcessStreamListener psl = new TestProcessStreamListener();
        String filter = registerProcessStreamListener(psl, "baz", "bam");

        doTestRespawnProcess(tmpFile, "0", filter, null /* lcFilter */);

        // Check whether our PSL is obtained and called...
        assertEquals(5, psl.m_setStdoutCallCount);
        assertEquals(5, psl.m_setStdinCallCount);

        String contents = TestUtil.slurpFile(tmpFile);
        assertTrue(contents, contents.matches("(?s)0.+1.+2.+3.+4.+"));
    }

    /**
     * Tests that a new process will be respawned, and its process lifecycle listener is called for
     * each respawn.
     * 
     * @throws Exception not part of this test case.
     */
    @Test
    public void testRespawnProcessWithProcessLifecycleListenersOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        File tmpFile = createEmptyTempFile();

        TestProcessLifecycleListener pll = new TestProcessLifecycleListener();
        String filter = registerProcessLifecycleListener(pll, "bar", "foo");

        doTestRespawnProcess(tmpFile, "0", null /* psFilter */, filter);

        // Check whether our PSL is obtained and called...
        assertEquals(5, pll.m_afterCallCount);
        assertEquals(5, pll.m_beforeCallCount);

        String contents = TestUtil.slurpFile(tmpFile);
        assertTrue(contents, contents.matches("(?s)0.+1.+2.+3.+4.+"));
    }

    /**
     * Creates an empty temporary file, that is deleted on exit of the JVM.
     * 
     * @return a file instance pointing to a new temporary file.
     * @throws IOException in case of I/O problems.
     */
    private File createEmptyTempFile() throws IOException {
        File tmpFile = File.createTempFile("pls", null);
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    /**
     * Actual test implementation for <tt>#testRespawnProcessWithExitValue*<tt>.
     * 
     * @param tmpFile the temporary file to write the script results to;
     * @param normalExitValue the exit value that should be considered as normal.
     * @throws IOException in case of I/O problems.
     */
    private void doTestRespawnProcess(File tmpFile, String normalExitValue, String psFilter, String lcFilter)
        throws IOException {

        int count = 4;
        String tmpFilename = tmpFile.getAbsolutePath();

        // Seems daunting, but what this command does is simply count the number
        // of lines in a given file (= tmpFile), and append this to that same
        // file; it uses this number to create an exit value, which counts from
        // <count> back to 0. This way, we can test whether the respawn
        // functionality works, as this currently checks for certain exit
        // values...
        String args =
            String.format("-c L=$(cat\\ %1$s\\ |\\ wc\\ -l)\\ &&\\ echo\\ $L\\ >>\\ %1$s\\ &&\\ exit\\ $((%2$d-$L))",
                tmpFilename, count);

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "1");
        launchConfig.put("executable.name", "/bin/bash");
        launchConfig.put("executable.args", args);
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "true");
        launchConfig.put("executable.normalExitValue", normalExitValue);
        if (psFilter != null) {
            launchConfig.put("executable.processStreamListener", psFilter);
        }
        if (lcFilter != null) {
            launchConfig.put("executable.processLifecycleListener", lcFilter);
        }

        configureFactory(ProcessLauncherServiceImpl.PID, launchConfig);

        // Wait until the processes are done...
        sleep(1000);
    }

    /**
     * Registers a given process stream listener and returns the filter clause to obtain that same
     * instance through OSGi.
     * 
     * @param processStreamListener the process stream listener to register, cannot be
     *        <code>null</code>.
     * @return the filter clause to obtain the exact same process stream listener through OSGi,
     *         never <code>null</code>.
     */
    private String registerProcessStreamListener(TestProcessStreamListener processStreamListener, String... properties) {
        assertEquals("Number of properties not a multiple of two!", 0, properties.length % 2);

        String className = ProcessStreamListener.class.getName();
        String extraFilter = "";

        Properties props = new Properties();
        for (int i = 0; i < properties.length; i += 2) {
            String key = properties[i];
            String value = properties[i + 1];

            extraFilter = String.format("%s(%s=%s)", extraFilter, key, value);
            props.setProperty(key, value);
        }

        m_dependencyManager.add(m_dependencyManager.createComponent().setInterface(className, props)
            .setImplementation(processStreamListener));

        if (extraFilter.trim().isEmpty()) {
            return String.format("(%s=%s)", Constants.OBJECTCLASS, className);
        }
        return String.format("(&(%s=%s)%s)", Constants.OBJECTCLASS, className, extraFilter);
    }

    /**
     * Registers a given process lifecycle listener and returns the filter clause to obtain that
     * same instance through OSGi.
     * 
     * @param processLifecycleListener the process lifecycle listener to register, cannot be
     *        <code>null</code>.
     * @return the filter clause to obtain the exact same process stream listener through OSGi,
     *         never <code>null</code>.
     */
    private String registerProcessLifecycleListener(TestProcessLifecycleListener processLifecycleListener,
        String... properties) {
        assertEquals("Number of properties not a multiple of two!", 0, properties.length % 2);

        String className = ProcessLifecycleListener.class.getName();
        String extraFilter = "";

        Properties props = new Properties();
        for (int i = 0; i < properties.length; i += 2) {
            String key = properties[i];
            String value = properties[i + 1];

            extraFilter = String.format("%s(%s=%s)", extraFilter, key, value);
            props.setProperty(key, value);
        }

        m_dependencyManager.add(m_dependencyManager.createComponent().setInterface(className, props)
            .setImplementation(processLifecycleListener));

        if (extraFilter.trim().isEmpty()) {
            return String.format("(%s=%s)", Constants.OBJECTCLASS, className);
        }
        return String.format("(&(%s=%s)%s)", Constants.OBJECTCLASS, className, extraFilter);
    }

    /**
     * Lazily initializes the configuration admin service and returns it.
     * 
     * @return the {@link ConfigurationAdmin} instance, never <code>null</code>.
     * @throws AssertionFailedError in case the {@link ConfigurationAdmin} service couldn't be
     *         obtained.
     */
    private ConfigurationAdmin getConfigAdmin() {
        ServiceTracker serviceTracker = new ServiceTracker(m_context, ConfigurationAdmin.class.getName(), null);

        ConfigurationAdmin instance = null;

        serviceTracker.open();
        try {
            instance = (ConfigurationAdmin) serviceTracker.waitForService(2 * 1000);

            if (instance == null) {
                fail("ConfigurationAdmin service not found!");
            }
            else {
                return instance;
            }
        }
        catch (InterruptedException e) {
            // Make sure the thread administration remains correct!
            Thread.currentThread().interrupt();

            e.printStackTrace();
            fail("ConfigurationAdmin service not available: " + e.toString());
        }

        return instance;
    }

    /**
     * Creates a factory configuration with the given properties, just like {@link #configure}.
     * 
     * @param factoryPid the PID of the factory that should be used to create a configuration;
     * @param properties the new configuration properties to configure, can be <code>null</code>.
     * @return The PID of newly created configuration.
     * @throws IOException when the configuration couldn't be set/updated.
     * @throws AssertionFailedError in case the {@link ConfigurationAdmin} service couldn't be
     *         obtained.
     */
    private String configureFactory(String factoryPid, Properties properties) throws IOException {
        assertNotNull("Parameter factoryPid cannot be null!", factoryPid);

        org.osgi.service.cm.Configuration config = getConfigAdmin().createFactoryConfiguration(factoryPid, null);
        config.update(properties);

        // Delay a bit to allow configuration to be propagated...
        sleep(500);

        return config.getPid();
    }

    /**
     * Testing implementation of {@link ProcessLifecycleListener}.
     */
    static final class TestProcessLifecycleListener implements ProcessLifecycleListener {
        private volatile int m_beforeCallCount = 0;
        private volatile int m_afterCallCount = 0;

        /**
         * {@inheritDoc}
         */
        public void afterProcessEnd(LaunchConfiguration configuration) {
            m_afterCallCount++;
        }

        /**
         * {@inheritDoc}
         */
        public Properties beforeProcessStart(LaunchConfiguration configuration) {
            m_beforeCallCount++;
            return null;
        }
    }

    /**
     * Testing implementation of {@link ProcessStreamListener}.
     */
    static class TestProcessStreamListener implements ProcessStreamListener {

        private volatile int m_setStdinCallCount = 0;
        private volatile int m_setStdoutCallCount = 0;

        /**
         * {@inheritDoc}
         */
        public void setStdin(LaunchConfiguration launchConfiguration, OutputStream outputStream) {
            m_setStdinCallCount++;
        }

        /**
         * {@inheritDoc}
         */
        public void setStdout(LaunchConfiguration launchConfiguration, InputStream inputStream) {
            m_setStdoutCallCount++;
        }

        /**
         * {@inheritDoc}
         */
        public boolean wantsStdin() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        public boolean wantsStdout() {
            return true;
        }
    }
}
