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
package org.apache.ace.it.deployment;

import static org.apache.ace.it.deployment.Constants.TEST_AUTH_SCHEME;
import static org.apache.ace.it.deployment.Constants.TEST_CUSTOMER;
import static org.apache.ace.it.deployment.Constants.TEST_HTTP_PORT;
import static org.apache.ace.it.deployment.Constants.TEST_TARGETID;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;

/**
 * Integration tests for ACE-330.
 */
public class Ace330Test extends IntegrationTestBase {
    // Injected by Felix DM...
    private volatile DeploymentProvider m_deploymentProvider;

    private String m_host;
    private String m_obrStorePath;

    private URL m_repoLocation;
    private URL m_obrLocation;

    /**
     * TODO this is only a placeholder test.
     */
    public void testInitiallyNoVersions() throws Exception {
        List<String> versions = m_deploymentProvider.getVersions(TEST_TARGETID);
        assertNotNull(versions);
        assertTrue(versions.isEmpty());
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        m_host = String.format("http://localhost:%d", TEST_HTTP_PORT);
        m_obrStorePath = new File("generated/store").getAbsolutePath();
        m_repoLocation = new URL(String.format("%s/repository", m_host));
        m_obrLocation = new URL(String.format("%s/obr/", m_host));

        String repoLocation = m_repoLocation.toExternalForm();
        String obrLocation = m_obrLocation.toExternalForm();

        // the various repositories...
        configureFactory("org.apache.ace.server.repository.factory", "name", "deployment", "customer", TEST_CUSTOMER, "master", "true");
        configureFactory("org.apache.ace.server.repository.factory", "name", "shop", "customer", TEST_CUSTOMER, "master", "true");
        configureFactory("org.apache.ace.server.repository.factory", "name", "target", "customer", TEST_CUSTOMER, "master", "true");
        configureFactory("org.apache.ace.server.repository.factory", "name", "user", "customer", TEST_CUSTOMER, "master", "true", "initial", TEST_AUTH_SCHEME);

        configure("org.apache.ace.client.repository", "showunregisteredtargets", "true", "deploymentversionlimit", "3", "obrlocation", obrLocation);
        configure("org.apache.ace.client.rest", 
            "repository.url", repoLocation, 
            "customer.name", TEST_CUSTOMER, 
            "store.repository.name", "shop",
            "distribution.repository.name", "target", 
            "deployment.repository.name", "deployment");

        configure("org.apache.ace.deployment.provider.repositorybased", "url", repoLocation, "name", "deployment", "customer", TEST_CUSTOMER);
        configure("org.apache.ace.deployment.servlet.agent", 
            "obr.url", obrLocation);

        configure("org.apache.ace.discovery.property", "serverURL", m_host);

        configure("org.apache.ace.identification.property", "targetID", TEST_TARGETID);
        
        configure("org.apache.ace.obr.storage.file", "fileLocation", m_obrStorePath);

        configure("org.apache.ace.http.context", "authentication.enabled", "false");
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        // Wait until one of important repositories is online...
        NetUtils.waitForURL(String.format("%s/repository/query?customer=%s&name=deployment", m_host, TEST_CUSTOMER));
    }

    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(DeploymentProvider.class).setRequired(true))
        };
    }
}
