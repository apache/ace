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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.builder.DeploymentPackageBuilder;
import org.apache.ace.it.IntegrationTestBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;

public abstract class BaseAgentTest extends IntegrationTestBase {

    protected static class TestBundle {
        private final File m_file;

        public TestBundle(String name, Version version, String... headers) throws Exception {
            m_file = createBundle(name, version, headers);
        }

        public File getFile() {
            return m_file;
        }
    }

    protected static class TestPackage {
        private final String m_name;
        private final Version m_version;
        private final File m_file;

        public TestPackage(String name, Version version, TestBundle... bundles) throws Exception {
            m_name = name;
            m_version = version;

            File[] files = new File[bundles.length];
            for (int i = 0; i < bundles.length; i++) {
                files[i] = bundles[i].getFile();
            }
            m_file = createPackage(m_name, m_version, files);
        }

        public String getName() {
            return m_name;
        }

        public File getFile() {
            return m_file;
        }

        public Version getVersion() {
            return m_version;
        }
    }

    protected static File createBundle(String bsn, Version version, String... headers) throws Exception {
        Builder b = new Builder();

        try {
            b.setProperty("Bundle-SymbolicName", bsn);
            b.setProperty("Bundle-Version", version.toString());
            for (int i = 0; i < headers.length; i += 2) {
                b.setProperty(headers[i], headers[i + 1]);
            }
            b.setProperty("Include-Resource", "bnd.bnd"); // prevent empty jar bug

            Jar jar = b.build();
            jar.getManifest(); // Not sure whether this is needed...

            File file = File.createTempFile("testbundle", ".jar");
            file.deleteOnExit();

            jar.write(file);
            return file;
        }
        finally {
            b.close();
        }
    }

    protected static File createPackage(String name, Version version, File... bundles) throws Exception {
        DeploymentPackageBuilder builder = DeploymentPackageBuilder.createDeploymentPackage(name, version.toString());

        OutputStream fos = null;
        try {
            for (File bundle : bundles) {
                builder.addBundle(bundle.toURI().toURL());
            }

            File file = File.createTempFile("testpackage", ".jar");
            file.deleteOnExit();

            fos = new FileOutputStream(file);
            builder.generate(fos);

            return file;
        }
        finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        // resetAgentBundleState();
    }

    protected Bundle getAgentBundle() {
        for (Bundle bundle : m_bundleContext.getBundles()) {
            if (bundle.getSymbolicName().equals(AgentControl.class.getPackage().getName())) {
                return bundle;
            }
        }
        throw new IllegalStateException("No agentBundle found");
    }

    protected void resetAgentBundleState() throws Exception {
        Bundle agentBundle = getAgentBundle();
        File dataDir = agentBundle.getBundleContext().getDataFile("");

        // System.out.println("BaseAgentTest: Stopping agent bundle");
        agentBundle.stop(Bundle.STOP_TRANSIENT);
        // System.out.println("BaseAgentTest: Cleaning bundle data dir (" + dataDir + ")");
        cleanDir(dataDir);
        // System.out.println("BaseAgentTest: Cleaning system properties");
        Set<String> keysBeRemoved = new HashSet<String>();
        for (Object key : System.getProperties().keySet()) {
            if (key instanceof String && ((String) key).startsWith(AgentConstants.CONFIG_KEY_NAMESPACE)) {
                keysBeRemoved.add((String) key);
            }
        }
        for (String removeKey : keysBeRemoved) {
            System.clearProperty(removeKey);
        }
        // System.out.println("BaseAgentTest: Starting agent bundle");
        agentBundle.start();
    }

    protected void configureAgent(ConfigurationHandler handler, String... configuration) {
        Map<String, String> config = new HashMap<String, String>();
        for (int i = 0; i < configuration.length; i += 2) {
            config.put(configuration[i], configuration[i + 1]);
        }
        handler.putAll(config);
    }

    private void cleanDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalStateException();
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                cleanDir(file);
            }
            file.delete();
        }
        dir.delete();
    }
}
