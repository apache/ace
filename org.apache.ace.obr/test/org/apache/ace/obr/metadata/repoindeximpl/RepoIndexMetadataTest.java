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
package org.apache.ace.obr.metadata.repoindeximpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.deployment.util.test.BundleStreamGenerator;
import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.metadata.repoindex.RepoIndexMetadataGenerator;
import org.testng.annotations.Test;

public class RepoIndexMetadataTest {

    private ArtifactData generateBundle(File file, String symbolicName, String version) throws Exception {
        // create a mock bundle, which is only used to generate the bundle on disk, and not used for anything else...
        ArtifactData bundle = new ArtifactDataImpl(file.getName(), symbolicName, -1L, version, file.toURI().toURL(), false);
        System.out.println("GETVERSION: " + bundle.getVersion());
        BundleStreamGenerator.generateBundle(bundle);
        return bundle;
    }

    /**
     * Generate metadata index, verify contents
     */
    @Test()
    public void generateMetaData() throws Exception {
        File dir = File.createTempFile("meta", "");
        dir.delete();
        dir.mkdir();
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.1", "1.0.0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.2", "1.0.0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.3", "1.0.0");
        MetadataGenerator meta = new RepoIndexMetadataGenerator();
        meta.generateMetadata(dir);
        File index = new File(dir, "index.xml");
        assert index.exists() : "No repository index was generated";
        assert index.length() > 0 : "Repository index can not be size 0";
        int count = 0;
        String line;
        BufferedReader in = new BufferedReader(new FileReader(index));
        while ((line = in.readLine()) != null) {
            if (line.contains("<resource>")) {
                count++;
            }
        }
        in.close();
        assert count == 3 : "Expected 3 resources in the repository index, found " + count + ".";
    }

    /**
     * Generate a metadata index, remove a bundle, regenerate metadata, verify.
     */
    @Test()
    public void updateMetaData() throws Exception {
        File dir = File.createTempFile("meta", "");
        dir.delete();
        dir.mkdir();
        File bundle = File.createTempFile("bundle", ".jar", dir);
        generateBundle(bundle, "bundle.symbolicname.1", "1.0.0");
        MetadataGenerator meta = new RepoIndexMetadataGenerator();
        meta.generateMetadata(dir);
        bundle.delete();
        meta.generateMetadata(dir);
        File index = new File(dir, "index.xml");
        assert index.exists() : "No repository index was generated";
        assert index.length() > 0 : "Repository index can not be size 0";
        int count = 0;
        String line;
        BufferedReader in = new BufferedReader(new FileReader(index));
        while ((line = in.readLine()) != null) {
            if (line.contains("<resource>")) {
                count++;
            }
        }
        in.close();
        assert count == 0 : "Expected 0 resources in the repository index, found " + count + ".";
    }

    /**
     * Generate metadata index with partially invalid contents, verify contents
     */
    @Test()
    public void generatePartiallyInvalidMetaData() throws Exception {
        File dir = File.createTempFile("meta", "");
        dir.delete();
        dir.mkdir();
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.1", "1.0.0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.2", "1.0_0");
        generateBundle(File.createTempFile("bundle", ".jar", dir), "bundle.symbolicname.3", "1.0.0");
        MetadataGenerator meta = new RepoIndexMetadataGenerator();
        meta.generateMetadata(dir);
        File index = new File(dir, "index.xml");
        assert index.exists() : "No repository index was generated";
        assert index.length() > 0 : "Repository index can not be size 0";
        int count = 0;
        String line;
        BufferedReader in = new BufferedReader(new FileReader(index));
        while ((line = in.readLine()) != null) {
            if (line.contains("<resource>")) {
                count++;
            }
        }
        in.close();
        assert count == 2 : "Expected 2 resources in the repository index, found " + count + ".";
    }
}
