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
package org.apache.ace.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DeploymentPackageBuilderTest {
    @Test()
    public void testEmptyDeploymentPackage() throws Exception {
        File tempFile = File.createTempFile("output-", ".jar");
        System.out.println("File: " + tempFile);
        FileOutputStream output = new FileOutputStream(tempFile);
        String name = "test";
        String version = "1.0.0";
        DeploymentPackageBuilder.createDeploymentPackage(name, version).generate(output);
        Manifest m = getManifest(tempFile);

        // the deployment package should have just a name and a version, but no entries
        Assert.assertEquals(name, m.getMainAttributes().getValue("DeploymentPackage-SymbolicName"));
        Assert.assertEquals(version, m.getMainAttributes().getValue("DeploymentPackage-Version"));
        Assert.assertTrue(m.getEntries().isEmpty());
    }

    @Test()
    public void testProcessorAndResourceDeploymentPackage() throws Exception {
        File tempFile = File.createTempFile("output-", ".jar");
        FileOutputStream output = new FileOutputStream(tempFile);
        String name = "test";
        String version = "1.0.0";

        String bundleSymbolicName = "bundle";
        String bundleVersion = "1.0.0";
        File tempBundleFile = File.createTempFile(bundleSymbolicName + "-" + bundleVersion + "-", ".jar");

        String pid = "my.processor";

        File tempArtifactFile = File.createTempFile("artifact-", ".jar");

        DeploymentPackageBuilder.createDeploymentPackage(name, version)
            .addResourceProcessor(createResourceProcessor(bundleSymbolicName, bundleVersion, pid, tempBundleFile))
            .addArtifact(createArtifact(pid, tempArtifactFile), pid)
            .generate(output);

        // the deployment package should have a name and a version, and a single entry (our bundle)
        Manifest m = getManifest(tempFile);
        Assert.assertEquals(name, m.getMainAttributes().getValue("DeploymentPackage-SymbolicName"));
        Assert.assertEquals(version, m.getMainAttributes().getValue("DeploymentPackage-Version"));
        Map<String, Attributes> entries = m.getEntries();
        Assert.assertEquals(2, entries.size());
        contains(entries.values(),
            "Bundle-SymbolicName", bundleSymbolicName,
            "Bundle-Version", bundleVersion,
            "DeploymentPackage-Customizer", "true",
            "Deployment-ProvidesResourceProcessor", pid);
        contains(entries.values(),
            "Resource-Processor", pid);
    }

    @Test(expectedExceptions = { Exception.class })
    public void testResourceWithoutProcessorDeploymentPackage() throws Exception {
        File tempFile = File.createTempFile("output-", ".jar");
        FileOutputStream output = new FileOutputStream(tempFile);
        String name = "test";
        String version = "1.0.0";

        String pid = "my.processor";

        File tempArtifactFile = File.createTempFile("artifact-", ".jar");

        DeploymentPackageBuilder.createDeploymentPackage(name, version)
            .addArtifact(createArtifact(pid, tempArtifactFile), pid)
            .generate(output);
    }

    @Test()
    public void testSingleBundleDeploymentPackage() throws Exception {
        File tempFile = File.createTempFile("output-", ".jar");
        FileOutputStream output = new FileOutputStream(tempFile);
        String name = "test";
        String version = "1.0.0";

        String bundleSymbolicName = "bundle";
        String bundleVersion = "1.0.0";
        File tempBundleFile = File.createTempFile(bundleSymbolicName + "-" + bundleVersion + "-", ".jar");

        DeploymentPackageBuilder.createDeploymentPackage(name, version)
            .addBundle(createBundle(bundleSymbolicName, bundleVersion, tempBundleFile))
            .generate(output);

        // the deployment package should have a name and a version, and a single entry (our bundle)
        Manifest m = getManifest(tempFile);
        Assert.assertEquals(name, m.getMainAttributes().getValue("DeploymentPackage-SymbolicName"));
        Assert.assertEquals(version, m.getMainAttributes().getValue("DeploymentPackage-Version"));
        Assert.assertEquals(1, m.getEntries().size());
        contains(m.getEntries().values(),
            "Bundle-SymbolicName", bundleSymbolicName,
            "Bundle-Version", bundleVersion);
    }

    @Test()
    public void testTwoBundleDeploymentPackage() throws Exception {
        File tempFile = File.createTempFile("output-", ".jar");
        FileOutputStream output = new FileOutputStream(tempFile);
        String name = "test";
        String version = "1.0.0";

        String bundleSymbolicName = "bundle";
        String bundleVersion = "1.0.0";
        File tempBundleFile = File.createTempFile(bundleSymbolicName + "-" + bundleVersion + "-", ".jar");

        String bundleSymbolicName2 = "bundle-two";
        String bundleVersion2 = "1.2.0";
        File tempBundleFile2 = File.createTempFile(bundleSymbolicName2 + "-" + bundleVersion2 + "-", ".jar");

        DeploymentPackageBuilder.createDeploymentPackage(name, version)
            .addBundle(createBundle(bundleSymbolicName, bundleVersion, tempBundleFile))
            .addBundle(createBundle(bundleSymbolicName2, bundleVersion2, tempBundleFile2))
            .generate(output);

        // the deployment package should have a name and a version, and a single entry (our bundle)
        Manifest m = getManifest(tempFile);
        Assert.assertEquals(name, m.getMainAttributes().getValue("DeploymentPackage-SymbolicName"));
        Assert.assertEquals(version, m.getMainAttributes().getValue("DeploymentPackage-Version"));
        Assert.assertEquals(2, m.getEntries().size());
        contains(m.getEntries().values(),
            "Bundle-SymbolicName", bundleSymbolicName,
            "Bundle-Version", bundleVersion);
        contains(m.getEntries().values(),
            "Bundle-SymbolicName", bundleSymbolicName2,
            "Bundle-Version", bundleVersion2);
    }

    private void contains(Collection<Attributes> list, String... keysAndValues) {
        for (Attributes attributes : list) {
            boolean found = true;
            for (int i = 0; i < keysAndValues.length; i += 2) {
                if (!keysAndValues[i + 1].equals(attributes.getValue(keysAndValues[i]))) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return;
            }
        }
        throw new IllegalStateException("Could not find entry in list.");
    }

    private URL createArtifact(String processorPID, File file) throws Exception {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(0);
            return file.toURI().toURL();
        }
        finally {
            if (output != null) {
                output.close();
            }
        }
    }

    private URL createBundle(String symbolicName, String version, File file) throws Exception {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Bundle-SymbolicName", symbolicName);
        main.putValue("Bundle-Version", version);

        JarOutputStream output = null;
        InputStream fis = null;
        try {
            output = new JarOutputStream(new FileOutputStream(file), manifest);
            return file.toURI().toURL();
        }
        finally {
            if (output != null) {
                output.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }

    private URL createResourceProcessor(String symbolicName, String version, String processorPID, File file) throws Exception {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Bundle-SymbolicName", symbolicName);
        main.putValue("Bundle-Version", version);
        main.putValue("DeploymentPackage-Customizer", "true");
        main.putValue("Deployment-ProvidesResourceProcessor", processorPID);

        JarOutputStream output = null;
        InputStream fis = null;
        try {
            output = new JarOutputStream(new FileOutputStream(file), manifest);
            return file.toURI().toURL();
        }
        finally {
            if (output != null) {
                output.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }

    private Manifest getManifest(File file) throws Exception {
        JarInputStream jis = new JarInputStream(file.toURI().toURL().openStream());
        Manifest bundleManifest = jis.getManifest();
        jis.close();
        return bundleManifest;
    }
}
