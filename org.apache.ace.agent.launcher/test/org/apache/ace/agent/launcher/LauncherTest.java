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
package org.apache.ace.agent.launcher;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.net.URL;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link Launcher}.
 */
public class LauncherTest {
    private static final String AGENT_DISCOVERY_SERVERURLS = "agent.discovery.serverurls";
    private static final String AGENT_ID = "agent.identification.agentid";

    private String m_testProperties;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        URL url = getClass().getResource("test.properties");
        assertNotNull(url);
        m_testProperties = new File(url.toURI()).getAbsolutePath();
    }

    @Test
    public void testParseAgentIdOk() throws Exception {
        Launcher launcher = new Launcher();

        launcher.parseArgs("-a", "id1");
        assertEquals(launcher.getConfiguration().get(AGENT_ID), "id1");

        launcher.parseArgs(AGENT_ID.concat("=id2"));
        assertEquals(launcher.getConfiguration().get(AGENT_ID), "id2");

        launcher.parseArgs("-c", m_testProperties);
        assertEquals(launcher.getConfiguration().get(AGENT_ID), "myAgentID");

        // -a supersedes agent.identification.agentid!
        launcher.parseArgs("-a", "id1", AGENT_ID.concat("=id2"));
        assertEquals(launcher.getConfiguration().get(AGENT_ID), "id1");

        // command line version supersedes the value in config file!
        launcher.parseArgs("-c", m_testProperties, AGENT_ID.concat("=id2"));
        assertEquals(launcher.getConfiguration().get(AGENT_ID), "id2");
    }

    @Test
    public void testParseServerURLsOk() throws Exception {
        Launcher launcher = new Launcher();

        launcher.parseArgs("-s", "a,b,c");
        assertEquals(launcher.getConfiguration().get(AGENT_DISCOVERY_SERVERURLS), "a,b,c");

        launcher.parseArgs(AGENT_DISCOVERY_SERVERURLS.concat("=d,e,f"));
        assertEquals(launcher.getConfiguration().get(AGENT_DISCOVERY_SERVERURLS), "d,e,f");

        launcher.parseArgs("-c", m_testProperties);
        assertEquals(launcher.getConfiguration().get(AGENT_DISCOVERY_SERVERURLS), "http://localhost:1234/");

        // -s supersedes agent.discovery.serverurls!
        launcher.parseArgs("-s", "a,b,c", AGENT_DISCOVERY_SERVERURLS.concat("=d,e,f"));
        assertEquals(launcher.getConfiguration().get(AGENT_DISCOVERY_SERVERURLS), "a,b,c");

        // command line version supersedes the value in config file!
        launcher.parseArgs("-c", m_testProperties, AGENT_DISCOVERY_SERVERURLS.concat("=d,e,f"));
        assertEquals(launcher.getConfiguration().get(AGENT_DISCOVERY_SERVERURLS), "d,e,f");
    }

    @Test
    public void testParseSystemPropertyOk() throws Exception {
        Launcher launcher = new Launcher();

        launcher.parseArgs("system.property=value", "-c", m_testProperties);
        // The system properties shouldn't be included in the configuration...
        assertNull(launcher.getConfiguration().get("property"));
        assertNull(launcher.getConfiguration().get("prop2"));
        // System properties never are accessible by their full name...
        assertNull(launcher.getProperty("system.property"));
        assertNull(launcher.getProperty("system.prop2"));
        // System properties are only accessible through their stripped name...
        assertEquals(launcher.getProperty("prop2"), "value2");
        assertEquals(launcher.getProperty("property"), "value");
    }
}
