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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

/**
 * {@link BundleProvider} that loads bundles from a directory.
 * 
 * @see META-INF/services/org.apache.ace.agent.launcher.BundleProvider
 */
public class BundleDirBundleProvider implements BundleProvider {
    public static final String BUNDLE_DIR_PROPERTY = "launcher.bundles.dir";
    public static final String BUNDLE_DIR_DEFAULT = "bundles";

    @Override
    public URL[] getBundles(PropertyProvider properties) throws IOException {
        File dir = getDir(properties);
        if (!dir.exists() || !dir.canRead() || !dir.isDirectory()) {
            return new URL[0];
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.ENGLISH).endsWith(".jar");
            }
        });
        URL[] result = new URL[files.length];
        for (int i = 0; i < files.length; i++) {
            result[i] = files[i].toURI().toURL();
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private File getDir(PropertyProvider provider) throws IOException {
        String dir = provider.getProperty(BUNDLE_DIR_PROPERTY);
        if (dir == null) {
            dir = BUNDLE_DIR_DEFAULT;
        }
        return new File(dir).getCanonicalFile();
    }
}
