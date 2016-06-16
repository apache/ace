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
package org.apache.ace.deployment.util.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.ace.deployment.provider.ArtifactData;
import org.osgi.framework.Constants;

import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public class BundleStreamGenerator {

    public static Manifest getBundleManifest(String symbolicname, String version, Map<String, String> additionalHeaders) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicname);
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, version.toString());
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            manifest.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        return manifest;
    }

    public static void generateBundle(ArtifactData data, Map<String, String> additionalHeaders) throws IOException {
        OutputStream bundleStream = null;
        try {
            File dataFile = new File(data.getUrl().toURI());
            OutputStream fileStream = new FileOutputStream(dataFile);
            bundleStream = new JarOutputStream(fileStream, getBundleManifest(data.getSymbolicName(), data.getVersion(), additionalHeaders));
            bundleStream.flush();
        } catch (URISyntaxException e) {
            throw new IOException();
        } finally {
            if (bundleStream != null) {
                bundleStream.close();
            }
        }
    }

    public static void generateBundle(ArtifactData data) throws IOException {
        generateBundle(data, new HashMap<String, String>());
    }
}
