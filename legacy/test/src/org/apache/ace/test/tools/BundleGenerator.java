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
package org.apache.ace.test.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.osgi.framework.Constants;

/**
 * Tool that generates test bundles.
 */
public class BundleGenerator {
    public static void main(String[] args) {

        if (args.length != 5) {
            System.out.println("Usage: java -jar BundleGenerator.jar [dir] [symbolicname] [version] [bundles] [size]");
            System.exit(5);
        }
        try {
            File destDir = new File(args[0]);
            String symbolicName = args[1];
            String version = args[2];
            int bundles = Integer.parseInt(args[3]);
            int size = Integer.parseInt(args[4]);

            if (destDir.exists()) {
                if (!destDir.isDirectory()) {
                    throw new Exception("Destination " + destDir + " is not a directory.");
                }
            }
            else {
                if (!destDir.mkdirs()) {
                    throw new Exception("Could not make directory: " + destDir);
                }
            }
            System.out.println("Generating " + bundles + " bundles in " + destDir + " called " + symbolicName + " version " + version + " with " + size + " bytes of data.");

            byte[] buffer = new byte[size];
            Random random = new Random();
            random.nextBytes(buffer);
            for (int i = 0; i < bundles; i++) {
                try {
                    createBundle(destDir, symbolicName + i, version, buffer);
                }
                catch (Exception e) {
                    System.err.println("Could not generate " + symbolicName + i + " version " + version + " because: " + e.getMessage());
                }
            }
        }
        catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(20);
        }

    }

    private static void createBundle(File destDir, String symbolicName, String version, byte[] data) throws FileNotFoundException, IOException {
        File bundle = new File(destDir, symbolicName + "-" + version + ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        manifest.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, version);
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(bundle), manifest);
        JarEntry entry = new JarEntry("data");
        jos.putNextEntry(entry);
        jos.write(data);
        jos.close();
    }
}
