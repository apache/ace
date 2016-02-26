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
package org.apache.ace.discovery.property;

import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.discovery.DiscoveryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.osgi.service.cm.ConfigurationException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class SimpleDiscoveryTest {

    private static final String SERVERURL_KEY = DiscoveryConstants.DISCOVERY_URL_KEY;
    private static final String VALID_URL = "http://test.url.com:" + TestConstants.PORT;
    private static final String INVALID_URL = "malformed url";

    private PropertyBasedDiscovery m_discovery;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_discovery = new PropertyBasedDiscovery();
    }

    /**
     * Test if setting a valid configuration is handled correctly
     * 
     * @throws Exception
     */
    @Test
    public void simpleDiscoveryValidConfiguration() throws ConfigurationException {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(SERVERURL_KEY, VALID_URL);
        m_discovery.updated(properties);
        URL url = m_discovery.discover();
        assert VALID_URL.equals(url.toString()) : "Configured url was not returned";
    }

    /**
     * Test if setting an invalid configuration is handled correctly.
     * 
     * @throws ConfigurationException
     */
    @Test(expectedExceptions = ConfigurationException.class)
    public void simpleDiscoveryInvalidConfiguration() throws ConfigurationException {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(SERVERURL_KEY, INVALID_URL);
        m_discovery.updated(properties);
    }

    /**
     * Test if supplying an empty configuration results in the service's default being used.
     * 
     * @throws ConfigurationException
     */
    @Test
    public void simpleDiscoveryEmptyConfiguration() throws ConfigurationException {
        // set valid config
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(SERVERURL_KEY, VALID_URL);
        m_discovery.updated(properties);
        // set empty config
        m_discovery.updated(null);
    }
}
