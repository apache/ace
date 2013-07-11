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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.Constants;

/**
 * Internal util class that represents some basic resource metadata and provides static methods to retrieve it.
 * 
 */
public class ResourceMetaData {

    // matches a valid OSGi version
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(\\.(\\d+)(\\.(\\d+)([\\.-]([\\w-]+))?)?)?");

    /**
     * Tries extract file metadata from a file assuming it is a valid OSGi bundle.
     * 
     * @param file
     *            the file to analyze
     * @return the metadata, or <code>null</code> if the file is not a valid bundle.
     */
    public static ResourceMetaData getBundleMetaData(File file) {
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(new FileInputStream(file));
            Manifest manifest = jis.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                if (attributes != null) {
                    String bundleSymbolicName = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
                    String bundleVersion = attributes.getValue(Constants.BUNDLE_VERSION);
                    if (bundleSymbolicName != null) {
                        // ACE-350 strip BSN parameters
                        if (bundleSymbolicName.indexOf(";") > 0) {
                            bundleSymbolicName = bundleSymbolicName.substring(0, bundleSymbolicName.indexOf(";"));
                        }
                        if (bundleVersion == null) {
                            bundleVersion = "0.0.0";
                        }
                        return new ResourceMetaData(bundleSymbolicName, bundleVersion, "jar");
                    }
                }
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
        finally {
            try {
                jis.close();
            }
            catch (IOException e) {
                // too bad
            }
        }
    }

    /**
     * Tries extract file metadata from a filename assuming a pattern. The version must be a valid OSGi version. If no
     * version is found the default "0.0.0" is returned. <br/>
     * <br/>
     * Filename pattern: <code>&lt;filename&gt;[-&lt;version&gt;][.&lt;extension&gt;]<code>
     * 
     * @param filename
     *            the filename to analyze
     * @return the metadata
     */
    public static ResourceMetaData getArtifactMetaData(String fileName) {
        if (fileName == null || fileName.equals("")) {
            return null;
        }
        String symbolicName = null;
        String version = null;
        String extension = null;

        // determine extension
        String[] fileNameParts = fileName.split("\\.");
        if (fileNameParts.length > 1) {
            extension = fileNameParts[fileNameParts.length - 1];
            symbolicName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        else {
            symbolicName = fileName;
        }

        // determine version
        int dashIndex = symbolicName.indexOf('-');
        while (dashIndex != -1 && version == null) {
            String versionCandidate = symbolicName.substring(dashIndex + 1);
            Matcher versionMatcher = VERSION_PATTERN.matcher(versionCandidate);
            if (versionMatcher.matches()) {
                symbolicName = symbolicName.substring(0, dashIndex);
                version = versionCandidate;
            }
            else {
                dashIndex = symbolicName.indexOf('-', dashIndex + 1);
            }
        }

        if (version == null) {
            version = "0.0.0";
        }
        return new ResourceMetaData(symbolicName, version, extension);
    }

    private final String m_bundleSymbolicName;
    private final String m_version;
    private final String m_extension;

    ResourceMetaData(String bundleSymbolicName, String version, String extension) {
        m_bundleSymbolicName = bundleSymbolicName;
        m_version = version;
        m_extension = extension;
    }

    public String getSymbolicName() {
        return m_bundleSymbolicName;
    }

    public String getVersion() {
        return m_version;
    }

    public String getExtension() {
        return m_extension;
    }
}
