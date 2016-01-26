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
package org.apache.ace.processlauncher.itest;

import static org.apache.ace.processlauncher.itest.TestUtil.getOSName;
import static org.apache.ace.processlauncher.itest.TestUtil.sleep;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.processlauncher.LaunchConfiguration;
import org.apache.ace.processlauncher.ProcessLauncherService;
import org.apache.ace.processlauncher.ProcessStreamListener;
import org.apache.felix.dm.Component;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

import junit.framework.AssertionFailedError;

/**
 * Integration test for {@link ProcessLauncherService}.
 */
public class ProcessLauncherServiceIntegrationTest extends IntegrationTestBase {

    private final BundleContext m_context = FrameworkUtil.getBundle(getClass()).getBundleContext();
    private ProcessLauncherService m_instance;

    /**
     * Tests that manually providing a launch configuration to a {@link ProcessLauncherService} will
     * cause a new process to be started and terminated.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessWithExitValueOneOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "2");
        launchConfig.put("executable.name", "/bin/sh");
        launchConfig.put("executable.args", "-c sleep\\ 1\\ &&\\ exit\\ 1");
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "false");
        launchConfig.put("executable.normalExitValue", 1);

        int launchConfigCount = m_instance.getLaunchConfigurationCount();
        int runningProcessCount = m_instance.getRunningProcessCount();

        configureFactory(ProcessLauncherService.PID, launchConfig);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Two instances...
        assertEquals(runningProcessCount + 2, m_instance.getRunningProcessCount());

        // Wait until the processes are done...
        sleep(1100);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Zero instances...
        assertEquals(runningProcessCount, m_instance.getRunningProcessCount());
    }

    /**
     * Tests that manually providing a launch configuration to a {@link ProcessLauncherService}
     * will cause a new process to be started and terminated.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessWithExitValueZeroOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "2");
        launchConfig.put("executable.name", "/bin/sh");
        launchConfig.put("executable.args", "-c sleep\\ 1\\ &&\\ exit\\ 0");
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "false");
        launchConfig.put("executable.normalExitValue", 0);

        int launchConfigCount = m_instance.getLaunchConfigurationCount();
        int runningProcessCount = m_instance.getRunningProcessCount();

        configureFactory(ProcessLauncherService.PID, launchConfig);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Two instances...
        assertEquals(runningProcessCount + 2, m_instance.getRunningProcessCount());

        // Wait until the processes are done...
        sleep(1100);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Zero instances...
        assertEquals(runningProcessCount, m_instance.getRunningProcessCount());
    }

    /**
     * Tests that registering multiple process stream listeners will cause the registered listener
     * with the highest service-ID to be called when a process is executed.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessWithMultipleRegisteredProcessStreamListenerOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        TestProcessStreamListener psl1 = new TestProcessStreamListener();
        registerProcessStreamListener(psl1, "qux", "quu");
        TestProcessStreamListener psl2 = new TestProcessStreamListener();
        String filter = registerProcessStreamListener(psl2, "qux", "quu");

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "1");
        launchConfig.put("executable.name", "/bin/sh");
        launchConfig.put("executable.args", "-c sleep\\ 1\\ &&\\ exit\\ 0");
        launchConfig.put("executable.processStreamListener", filter);
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "false");
        launchConfig.put("executable.normalExitValue", 0);

        int launchConfigCount = m_instance.getLaunchConfigurationCount();
        int runningProcessCount = m_instance.getRunningProcessCount();

        configureFactory(ProcessLauncherService.PID, launchConfig);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Two instances...
        assertEquals(runningProcessCount + 1, m_instance.getRunningProcessCount());

        // Wait until the processes are done...
        sleep(1100);

        // Check whether our PSL is obtained and called...
        assertEquals(1, psl1.m_setStdoutCallCount);
        assertEquals(1, psl1.m_setStdinCallCount);
        assertEquals(0, psl2.m_setStdoutCallCount);
        assertEquals(0, psl2.m_setStdinCallCount);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Zero instances...
        assertEquals(runningProcessCount, m_instance.getRunningProcessCount());
    }

    /**
     * Tests that an unregistered process stream listener will cause an exception to be thrown when
     * a process is to be started.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessWithoutRegisteredProcessStreamListenerOnUnixBasedHostsFail() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        TestProcessStreamListener psl = new TestProcessStreamListener();
        String filter =
            String.format("(&(%s=%s)(qux=quu))", Constants.OBJECTCLASS, ProcessStreamListener.class.getName());

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "1");
        launchConfig.put("executable.name", "/bin/sh");
        launchConfig.put("executable.args", "-c sleep\\ 1\\ &&\\ exit\\ 0");
        launchConfig.put("executable.processStreamListener", filter);
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "false");
        launchConfig.put("executable.normalExitValue", 0);

        int launchConfigCount = m_instance.getLaunchConfigurationCount();
        int runningProcessCount = m_instance.getRunningProcessCount();

        configureFactory(ProcessLauncherService.PID, launchConfig);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Two instances...
        assertEquals(runningProcessCount + 1, m_instance.getRunningProcessCount());

        // Wait until the processes are done...
        sleep(1100);

        // Check whether our PSL is obtained and called...
        assertEquals(0, psl.m_setStdoutCallCount);
        assertEquals(0, psl.m_setStdinCallCount);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Zero instances...
        assertEquals(runningProcessCount, m_instance.getRunningProcessCount());
    }

    /**
     * Tests that registering a process stream listener will cause the registered listener to be
     * called when a process is executed.
     * 
     * @throws Exception not part of this test case.
     */
    public void testLaunchProcessWithRegisteredProcessStreamListenerOnUnixBasedHostsOk() throws Exception {
        // Test will not work on Windows!
        if (getOSName().contains("windows")) {
            return;
        }

        TestProcessStreamListener psl = new TestProcessStreamListener();
        String filter = registerProcessStreamListener(psl, "foo", "bar");

        Properties launchConfig = new Properties();
        launchConfig.put("instance.count", "1");
        launchConfig.put("executable.name", "/bin/sh");
        launchConfig.put("executable.args", "-c sleep\\ 1\\ &&\\ exit\\ 0");
        launchConfig.put("executable.processStreamListener", filter);
        launchConfig.put("executable.workingDir", "/tmp");
        launchConfig.put("executable.respawnAutomatically", "false");
        launchConfig.put("executable.normalExitValue", 0);

        int launchConfigCount = m_instance.getLaunchConfigurationCount();
        int runningProcessCount = m_instance.getRunningProcessCount();

        configureFactory(ProcessLauncherService.PID, launchConfig);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Two instances...
        assertEquals(runningProcessCount + 1, m_instance.getRunningProcessCount());

        // Wait until the processes are done...
        sleep(1100);

        // Check whether our PSL is obtained and called...
        assertEquals(1, psl.m_setStdoutCallCount);
        assertEquals(1, psl.m_setStdinCallCount);

        // One process...
        assertEquals(launchConfigCount + 1, m_instance.getLaunchConfigurationCount());
        // Zero instances...
        assertEquals(runningProcessCount, m_instance.getRunningProcessCount());
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

        Dictionary<String, Object> props = new Hashtable<>();
        for (int i = 0; i < properties.length; i += 2) {
            String key = properties[i];
            String value = properties[i + 1];

            extraFilter = String.format("%s(%s=%s)", extraFilter, key, value);
            props.put(key, value);
        }
        
        m_context.registerService(className, processStreamListener, props);

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
        ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> serviceTracker = new ServiceTracker<>(m_context, ConfigurationAdmin.class, null);

        ConfigurationAdmin instance = null;

        serviceTracker.open();
        try {
            instance = serviceTracker.waitForService(2 * 1000);

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

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(ProcessLauncherService.class).setRequired(true))
        };
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
     * {@link ProcessStreamListener} implementation for the test cases in this test.
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
