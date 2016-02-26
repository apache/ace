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
package org.apache.ace.obr.metadata.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.osgi.framework.Constants;
import org.testng.annotations.Test;

public class ResourceMetaDataTest {

    @Test()
    public void checkArtifactMetadataGeneration() throws Exception {
        ResourceMetaData data = ResourceMetaData.getArtifactMetaData("foo.bar-1.0.3.xml");
        assert "foo.bar".equals(data.getSymbolicName()) : "Generated symbolic name should be 'foo.bar', was " + data.getSymbolicName();
        assert "1.0.3".equals(data.getVersion()) : "Generated version should be '1.0.3', was " + data.getVersion();
        assert "xml".equals(data.getExtension()) : "Extension should be 'xml', was " + data.getExtension();
    }

    @Test()
    public void checkConfigurationTemplateMetadataGeneration() throws Exception {
        ResourceMetaData data = ResourceMetaData.getArtifactMetaData("org.foo.configuration-1.0.0.xml-target-1-2.0.0.xml");
        assert "org.foo.configuration-1.0.0.xml-target-1".equals(data.getSymbolicName()) : "Generated symbolic name should be 'org.foo.configuration-1.0.0.xml-target-1', was " + data.getSymbolicName();
        assert "2.0.0".equals(data.getVersion()) : "Generated version should be '2.0.0', was " + data.getVersion();
        assert "xml".equals(data.getExtension()) : "Extension should be 'xml', was " + data.getExtension();
    }

    @Test()
    public void checkBundleMetadataGeneration() throws Exception {
        ResourceMetaData data = ResourceMetaData.getBundleMetaData(createBundle("foo.bar", "1.0.3"));
        assert "foo.bar".equals(data.getSymbolicName()) : "Generated symbolic name should be 'foo.bar', was " + data.getSymbolicName();
        assert "1.0.3".equals(data.getVersion()) : "Generated version should be '1.0.3', was " + data.getVersion();
        assert "jar".equals(data.getExtension()) : "Extension should be 'xml', was " + data.getExtension();
    }

    private File createBundle(String symbolicName, String version) throws IOException {
        File tmpFile = File.createTempFile("tmpbundle-", "jar");
        tmpFile.deleteOnExit();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (symbolicName != null) {
            manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
        }
        if (version != null) {
            manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, version);
        }
        JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpFile), manifest);
        target.close();
        return tmpFile;
    }
}
