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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.Properties;

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
    private ConfigurationAdmin m_configAdmin;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        setUp(false);
    }

    /**
     * Sets up the environment for testing.
     * @param reconfig Indicates whether or not the configurator should use reconfiguration.
     */
    protected void setUp(boolean reconfig) throws Exception {
        m_configAdmin = new MockConfigAdmin();

        m_configDir = FileUtils.createTempFile(null);
        m_configDir.mkdir();
        m_configurator = new Configurator(m_configDir, 400, reconfig);

        TestUtils.configureObject(m_configurator, ConfigurationAdmin.class, m_configAdmin);
        TestUtils.configureObject(m_configurator, LogService.class);
        TestUtils.configureObject(m_configurator, BundleContext.class, TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public String getProperty(String key) {
                return "contextProp";
            }
        }));
        m_configurator.start();
    }

    /**
     * save the properties into a configuration file the configurator can read.
     * The file is first created and then moved to make sure the configuration doesn't read an empty file
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
        } catch (IOException ioe) {
            // the test will fail, ignore this.
        } finally {
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
                outFile.renameTo(dest);
            }
            else {
                File file = new File(m_configDir, factoryPid);
                file.mkdirs();
                File dest = new File(file, servicePid + ".cfg");
                if (dest.exists()) {
                    dest.delete();
                }
                outFile.renameTo(dest);
            }
        }
    }

    // remove a created configuration file
    private void removeConfiguration(String servicePid) {
        removeConfiguration(servicePid, null);
    }

    private void removeConfiguration(String servicePid, String factoryPid) {
        if (factoryPid != null) {
            new File(m_configDir, factoryPid + File.separator + servicePid + ".cfg").delete();
        } else {
            new File(m_configDir, servicePid + ".cfg").delete();
        }
    }

    // set some standard properties for testing
    private Properties createProperties() {
        Properties props = new Properties();
        props.put("test", "value1");
        props.put("test2", "value2");
        return props;
    }

    // add a configuration
    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testAddConfiguration() {
        Properties initialConfiguration = createProperties();
        saveConfiguration("test-add", initialConfiguration);

        Dictionary configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(createProperties()) : "Configuration content is unexpected";
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testAddFactoryConfiguration() {
        Properties props = createProperties();
        saveConfiguration("test-add", "testFactory", props);

        Dictionary configuration = getAndWaitForConfiguration(props);
        assert configuration != null : "No configuration received from configurator";
        assert "testFactory_test-add".equals(configuration.remove("factory.instance.pid")) : "Incorrect factory instance pid was added to the configuration";
        assert configuration.equals(createProperties()) : "Configuration content is unexpected";
    }

    // remove a configuration
    @Test(groups = { UNIT })
    public void testRemoveFactoryConfiguration() {
        Properties props = createProperties();
        saveConfiguration("test-remove", "testFactory", props);
        getAndWaitForConfiguration(props);

        removeConfiguration("test-remove", "testFactory");

        // after some processing time, we should get a message that the configuration is now removed.
        long startTimeMillis = System.currentTimeMillis();
        boolean isDeleted = false;
        try {
            while (!isDeleted && (System.currentTimeMillis() < startTimeMillis + 2000)) {
                isDeleted = ((MockConfiguration) m_configAdmin.getConfiguration("")).isDeleted();
                if (!isDeleted) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException ie) {
            // not much we can do
        }
        catch (IOException e) {
            // cannot come from our mock config admin
        }
        assert isDeleted : "The configuration is not removed as expected";
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testPropertySubstitution( ) {
        Properties initialConfiguration = createProperties();
        initialConfiguration.put("var", "value");
        initialConfiguration.put("subst", "${var}");
        saveConfiguration("test-subst", initialConfiguration);

        Dictionary configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.get("subst").equals(configuration.get("var")) : "Substitution failed";
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testPropertySubstitutionFromContext() {
        Properties initialConfiguration = createProperties();
        initialConfiguration.put("subst", "${var}");
        saveConfiguration("test-subst", initialConfiguration);

        Dictionary configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.get("subst") != null : "Substitution failed";
    }

    // update a configuration, only adding a key (this is allowed in all cases)
    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testChangeConfigurationUsingNewKey() {
        Properties initialConfiguration = createProperties();
        saveConfiguration("test-change", initialConfiguration);

        Dictionary configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(initialConfiguration) : "Configuration content not expected. Was expecting " + initialConfiguration.size() + " but got " + configuration.size();

        initialConfiguration.put("anotherKey","anotherValue");
        saveConfiguration("test-change", initialConfiguration);

        // now the configuration should be updated
        configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(initialConfiguration) : "Configuration content not expected. Was expecting " + initialConfiguration.size() + " but got " + configuration.size();
    }

    // update a configuration, changing an already existing key, not using reconfiguration
    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testChangeConfigurationUsingSameKeyNoReconfigure() {
        Properties configurationValues = createProperties();
        Properties initialConfigurationValues = new Properties();
        initialConfigurationValues.putAll(configurationValues);
        saveConfiguration("test-change", configurationValues);

        Dictionary configuration = getAndWaitForConfiguration(configurationValues);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(configurationValues) : "Configuration content not expected. Was expecting " + configurationValues.size() + " but got " + configuration.size();

        configurationValues.put("test","value42");
        saveConfiguration("test-change", configurationValues);

        // The update should have been ignored, and the old values should still be present.
        configuration = getAndWaitForConfiguration(configurationValues);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(initialConfigurationValues) : "Configuration content not expected. Was expecting " + configurationValues.size() + " but got " + configuration.size();
    }

    // update a configuration, changing an already existing key, using reconfiguration
    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testChangeConfigurationUsingSameKeyWithReconfigure() throws Exception {
        setUp(true); // Instruct the configurator to reconfigure
        Properties configurationValues = createProperties();
        saveConfiguration("test-change", configurationValues);

        Dictionary configuration = getAndWaitForConfiguration(configurationValues);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(configurationValues) : "Configuration content not expected. Was expecting " + configurationValues.size() + " but got " + configuration.size();

        configurationValues.put("test","value42");
        saveConfiguration("test-change", configurationValues);

        // now the configuration should be updated
        configuration = getAndWaitForConfiguration(configurationValues);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(configurationValues) : "Configuration content not expected. Was expecting " + configurationValues.size() + " but got " + configuration.size();
    }

    // remove a configuration
    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testRemoveConfiguration() {
        Properties initialConfiguration = createProperties();
        saveConfiguration("test-remove", initialConfiguration);

        Dictionary configuration = getAndWaitForConfiguration(initialConfiguration);
        assert configuration != null : "No configuration received from configurator";
        assert configuration.equals(createProperties()) : "Configuration content is unexpected";

        // ok, the configuration is done.
        // now try to remove it.
        removeConfiguration("test-remove");

        // after some processing time, we should get a message that the configuration is now removed.
        long startTimeMillis = System.currentTimeMillis();
        boolean isDeleted = false;
        try {
            while (!isDeleted && (System.currentTimeMillis() < startTimeMillis + 2000)) {
                isDeleted = ((MockConfiguration) m_configAdmin.getConfiguration("")).isDeleted();
                if (!isDeleted) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException ie) {
            // not much we can do
        }
        catch (IOException e) {
            // cannot come from our mock config admin
        }
        assert isDeleted : "The configuration is not removed as expected";
    }

    /**
     * Get the configuration and if it not available yet wait for it.
     * If there is still no configuration after the wait time,
     * null is returned.
     */
    @SuppressWarnings("unchecked")
    public Dictionary getAndWaitForConfiguration(Dictionary expectedConfiguration) {
        long startTimeMillis = System.currentTimeMillis();
        // make sure we iterate at least once
        Dictionary configuration = null;
        try {
            boolean success = false;
            while (!success && (System.currentTimeMillis() < startTimeMillis + 2000)) {
                configuration = m_configAdmin.getConfiguration("").getProperties();
                if (configuration != null) {
                    synchronized(configuration) {
                        if (expectedConfiguration.equals(configuration)) {
                            success = true;
                        }
                    }
                }
                if (!success) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException ie) {
            // not much we can do
        }
        catch (IOException e) {
            // cannot come from our mock config admin
        }
        return configuration;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        m_configurator.stop();
        FileUtils.removeDirectoryWithContent(m_configDir);
    }
}
