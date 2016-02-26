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
package org.apache.ace.configurator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConfiguratorTest {
    private Configurator m_configurator;
    private File m_configDir;
    private MockConfigAdmin m_configAdmin;

    private volatile CountDownLatch m_deleteLatch;
    private volatile CountDownLatch m_updateLatch;

    @Test()
    public void testAddConfiguration() throws Exception {
        String pid = "test-add";

        Properties initialConfiguration = createProperties();
        saveConfiguration(pid, initialConfiguration);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(createProperties(), configuration, "Configuration content is unexpected");
    }

    @Test()
    public void testAddFactoryConfiguration() throws Exception {
        String pid = "test-add";
        String factoryPID = "testFactory";

        Properties props = createProperties();
        saveConfiguration(pid, "testFactory", props);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(factoryPID);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(configuration.remove("factory.instance.pid"), "testFactory_test-add", "Incorrect factory instance pid was added to the configuration");
        assertEquals(createProperties(), configuration, "Configuration content is unexpected");
    }

    // update a configuration, only adding a key (this is allowed in all cases)
    @Test()
    public void testChangeConfigurationUsingNewKey() throws Exception {
        String pid = "test-change";

        Properties initialConfiguration = createProperties();
        saveConfiguration(pid, initialConfiguration);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(initialConfiguration, configuration);

        initialConfiguration.put("anotherKey", "anotherValue");
        saveConfiguration("test-change", initialConfiguration);

        // now the configuration should be updated
        configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(initialConfiguration, configuration);
    }

    // update a configuration, changing an already existing key, not using reconfiguration
    @Test()
    public void testChangeConfigurationUsingSameKeyNoReconfigure() throws Exception {
        String pid = "test-change";

        Properties configurationValues = createProperties();
        Properties initialConfigurationValues = new Properties();
        initialConfigurationValues.putAll(configurationValues);
        saveConfiguration(pid, configurationValues);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(configurationValues, configuration);

        configurationValues.put("test", "value42");
        saveConfiguration("test-change", configurationValues);

        // The update should have been ignored, and the old values should still be present.
        configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(initialConfigurationValues, configuration);
    }

    // update a configuration, changing an already existing key, using reconfiguration
    @Test()
    public void testChangeConfigurationUsingSameKeyWithReconfigure() throws Exception {
        String pid = "test-change";

        setUp(true); // Instruct the configurator to reconfigure
        Properties configurationValues = createProperties();
        saveConfiguration(pid, configurationValues);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(configurationValues, configuration);

        configurationValues.put("test", "value42");
        saveConfiguration(pid, configurationValues);

        // now the configuration should be updated
        configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(configurationValues, configuration);
    }

    @Test()
    public void testPropertySubstitution() throws Exception {
        String pid = "test-subst";

        Properties initial = new Properties();
        initial.put("key1", "leading ${foo.${bar}} middle ${baz} trailing");
        initial.put("bar", "a");
        initial.put("foo.a", "text");
        initial.put("baz", "word");
        // ACE-401: use some weird log4j conversion pattern in our config file, should not confuse the Configurator's
        // substitution algorithm...
        initial.put("key2", "%d{ISO8601} | %-5.5p | %C | %X{bundle.name} | %m%n");
        // unknown and partially unknown variables shouldn't get substituted...
        initial.put("key3", "${qux} ${quu.${bar}} ${baz.${bar}}");
        saveConfiguration(pid, initial);

        Dictionary<String, ?> config = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(config, "No configuration received from configurator");
        assertEquals(config.get("key1"), "leading text middle word trailing", "Substitution failed!");
        assertEquals(config.get("key2"), "%d{ISO8601} | %-5.5p | %C | %X{bundle.name} | %m%n", "Substitution failed!");
        assertEquals(config.get("key3"), "${qux} ${quu.${bar}} ${baz.${bar}}", "Substitution failed!");
    }

    @Test()
    public void testPropertySubstitutionFromContext() throws Exception {
        String pid = "test-subst";

        Properties initialConfiguration = createProperties();
        initialConfiguration.put("subst", "${contextProp}");
        saveConfiguration(pid, initialConfiguration);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(configuration.get("subst"), "contextVal", "Substitution failed");
    }

    // remove a configuration
    @Test()
    public void testRemoveConfiguration() throws Exception {
        String pid = "test-remove";

        Properties initialConfiguration = createProperties();
        saveConfiguration(pid, initialConfiguration);

        Dictionary<String, ?> configuration = getAndWaitForConfigurationUpdate(pid);
        assertNotNull(configuration, "No configuration received from configurator");
        assertEquals(createProperties(), configuration);

        // ok, the configuration is done.
        // now try to remove it.
        removeConfiguration(pid);

        // after some processing time, we should get a message that the configuration is now removed.
        waitForConfigurationDelete(pid);
    }

    // remove a configuration
    @Test()
    public void testRemoveFactoryConfiguration() throws Exception {
        String pid = "test-remove";
        String factoryPID = "testFactory";

        Properties props = createProperties();
        saveConfiguration(pid, factoryPID, props);
        getAndWaitForConfigurationUpdate(factoryPID);

        removeConfiguration(pid, factoryPID);

        // after some processing time, we should get a message that the configuration is now removed.
        waitForConfigurationDelete(factoryPID);
    }

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        setUp(false);
    }

    /**
     * Sets up the environment for testing.
     * 
     * @param reconfig
     *            Indicates whether or not the configurator should use reconfiguration.
     */
    protected void setUp(boolean reconfig) throws Exception {
        m_configAdmin = new MockConfigAdmin() {
            @Override
            void configDeleted(MockConfiguration config) {
                m_deleteLatch.countDown();
            }

            @Override
            void configUpdated(MockConfiguration config) {
                m_updateLatch.countDown();
            }
        };

        m_configDir = FileUtils.createTempFile(null);
        m_configDir.mkdir();
        m_configurator = new Configurator(m_configDir, 200, reconfig);

        TestUtils.configureObject(m_configurator, ConfigurationAdmin.class, m_configAdmin);
        TestUtils.configureObject(m_configurator, LogService.class);
        TestUtils.configureObject(m_configurator, BundleContext.class, TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public String getProperty(String key) {
                if ("contextProp".equals(key)) {
                    return "contextVal";
                }
                return null;
            }
        }));
        m_configurator.start();
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws Exception {
        m_configurator.stop();
        FileUtils.removeDirectoryWithContent(m_configDir);

        m_deleteLatch = null;
        m_updateLatch = null;
    }

    // set some standard properties for testing
    private Properties createProperties() {
        Properties props = new Properties();
        props.put("test", "value1");
        props.put("test2", "value2");
        return props;
    }

    /**
     * Get the configuration and if it not available yet wait for it. If there is still no configuration after the wait
     * time, null is returned.
     */
    private Dictionary<String, ?> getAndWaitForConfigurationUpdate(String pid) throws Exception {
        assertTrue(m_updateLatch.await(2, TimeUnit.SECONDS));

        return m_configAdmin.getConfiguration(pid).getProperties();
    }

    // remove a created configuration file
    private void removeConfiguration(String servicePid) {
        removeConfiguration(servicePid, null);
    }

    private void removeConfiguration(String servicePid, String factoryPid) {
        if (factoryPid != null) {
            new File(m_configDir, factoryPid + File.separator + servicePid + ".cfg").delete();
        }
        else {
            new File(m_configDir, servicePid + ".cfg").delete();
        }

        m_deleteLatch = new CountDownLatch(1);
    }

    /**
     * Renames a given source file to a new destination file, using Commons-IO.
     * <p>
     * This avoids the problem mentioned in ACE-155.
     * </p>
     * 
     * @param source
     *            the file to rename;
     * @param dest
     *            the file to rename to.
     */
    private void renameFile(File source, File dest) {
        try {
            org.apache.commons.io.FileUtils.moveFile(source, dest);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to rename file!", e);
        }
    }

    /**
     * save the properties into a configuration file the configurator can read. The file is first created and then moved
     * to make sure the configuration doesn't read an empty file
     */
    private void saveConfiguration(String servicePid, Properties configuration) {
        saveConfiguration(servicePid, null, configuration);
    }

    /**
     * save the properties into a configuration file stored in a directory reflecting the factory pid
     */
    private void saveConfiguration(String servicePid, String factoryPid, Properties configuration) {
        OutputStream fileOutputStream = null;
        File outFile = null;
        try {
            outFile = FileUtils.createTempFile(null);
            fileOutputStream = new FileOutputStream(outFile);
            configuration.store(fileOutputStream, null);
        }
        catch (IOException ioe) {
            // the test will fail, ignore this.
        }
        finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                }
                catch (IOException e) {
                    // nothing we can do
                }
            }
        }
        if (outFile != null) {
            if (factoryPid == null) {
                File dest = new File(m_configDir, servicePid + ".cfg");
                if (dest.exists()) {
                    dest.delete();
                }
                renameFile(outFile, dest);
            }
            else {
                File file = new File(m_configDir, factoryPid);
                file.mkdirs();
                File dest = new File(file, servicePid + ".cfg");
                if (dest.exists()) {
                    dest.delete();
                }
                renameFile(outFile, dest);
            }
        }

        m_updateLatch = new CountDownLatch(1);
    }

    /**
     * Get the configuration and if it not available yet wait for it. If there is still no configuration after the wait
     * time, null is returned.
     */
    private void waitForConfigurationDelete(String pid) throws Exception {
        assertTrue(m_deleteLatch.await(2, TimeUnit.SECONDS));
    }
}
