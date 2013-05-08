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
package org.apache.ace.agent.itest;

import java.util.Dictionary;
import java.util.Hashtable;

import junit.framework.Assert;

import org.apache.ace.agent.ManagementAgent;
import org.apache.ace.it.IntegrationTestBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ManagedService;

/**
 * Integration test for Management Agent Configuration
 * 
 */
public class ManagementAgentTest extends IntegrationTestBase {

    /**
     * One basic agent using CM API
     * 
     */
    public void testOneAgentConfiguration() throws Exception {

        // agent factory should be up
        ManagedService agentConfiguration = getService(ManagedService.class, "(" + Constants.SERVICE_PID + "=" + org.apache.ace.agent.Constants.CONFIG_PID + ")");
        Assert.assertNotNull(agentConfiguration);

        assertAgentDown("007");

        // configure an agent
        Dictionary<String, String> config = new Hashtable<String, String>();
        config.put("verbose", "true");
        config.put("agents", "007");
        config.put("serverurl", "http://localhost:8080");
        config.put("logstores", "auditlog");
        agentConfiguration.updated(config);

        assertAgentUp("007");

        config = new Hashtable<String, String>();
        config.put("verbose", "true");
        agentConfiguration.updated(config);

        assertAgentDown("007");
    }

    /**
     * Two basic agents using static config
     * 
     */
    public void testTwoAgentsConfiguration() throws Exception {

        // agent factory should be up
        ManagedService agentConfiguration = getService(ManagedService.class, "(" + Constants.SERVICE_PID + "=" + org.apache.ace.agent.Constants.CONFIG_PID + ")");
        Assert.assertNotNull(agentConfiguration);

        assertAgentDown("007");
        assertAgentDown("009");

        // configure an agent
        Dictionary<String, String> config = new Hashtable<String, String>();
        config.put("verbose", "true");
        config.put("agents", "007,009");
        config.put("serverurl", "http://localhost:8080");
        config.put("logstores", "auditlog");
        agentConfiguration.updated(config);

        assertAgentUp("007");
        assertAgentUp("009");

        config = new Hashtable<String, String>();
        config.put("verbose", "true");
        agentConfiguration.updated(config);

        assertAgentDown("007");
        assertAgentDown("009");
    }

    private void assertAgentUp(String agentId) throws InvalidSyntaxException {

        String agentFilter = "(agent=" + agentId + ")";

        ManagementAgent agent = getService(ManagementAgent.class, agentFilter);
        assertNotNull(agent);

        ServiceReference[] references = m_bundleContext.getAllServiceReferences("org.apache.ace.identification.Identification", agentFilter);
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences("org.apache.ace.discovery.Discovery", agentFilter);
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences("org.apache.ace.deployment.Deployment", agentFilter);
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences("org.apache.ace.deployment.service.DeploymentService", agentFilter);
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences("org.apache.ace.log.Log", "(&" + agentFilter + "(name=auditlog))");
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences("org.apache.ace.log.target.store.LogStore", "(&" + agentFilter + "(name=auditlog))");
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences(Runnable.class.getName(), "(&" + agentFilter + "(name=auditlog))");
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences(Runnable.class.getName(), "(&" + agentFilter + "(taskName=DeploymentUpdateTask))");
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences(Runnable.class.getName(), "(&" + agentFilter + "(taskName=DeploymentCheckTask))");
        assertNotNull(references);
        assertEquals(1, references.length);

        references = m_bundleContext.getAllServiceReferences(Runnable.class.getName(), "(&" + agentFilter + "(taskName=LogSyncTask)(name=auditlog))");
        assertNotNull(references);
        assertEquals(1, references.length);
    }

    private void assertAgentDown(String agentId) throws InvalidSyntaxException {
        String agentFilter = "(agent=" + agentId + ")";
        ServiceReference[] references = m_bundleContext.getAllServiceReferences(ManagementAgent.class.getName(), agentFilter);
        assertNull(references);
    }

    private void restartBundle(String bsn) throws BundleException {
        for (Bundle bundle : m_bundleContext.getBundles()) {
            if (bundle.getSymbolicName().equals(bsn)) {
                bundle.stop();
                bundle.start();
            }
        }
    }
}
