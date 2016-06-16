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

import java.net.URL;

import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public class ArtifactData {
    private final URL m_url;
    private final String m_filename;
    private boolean m_isBundle;
    private String m_bundleSymbolicName;
    private String m_bundleVersion;
    private boolean m_isCustomizer;
    private String m_processorPID;

    private ArtifactData(URL url, String filename) {
        m_url = url;
        m_filename = filename;
    }

    public static ArtifactData createBundle(URL url, String filename, String bundleSymbolicName, String bundleVersion) {
        ArtifactData data = new ArtifactData(url, filename);
        data.setBundleMetadata(bundleSymbolicName, bundleVersion);
        return data;
    }

    public static ArtifactData createResourceProcessor(URL url, String filename, String bundleSymbolicName, String bundleVersion, String processorPID) {
        ArtifactData data = new ArtifactData(url, filename);
        data.setBundleMetadata(bundleSymbolicName, bundleVersion);
        data.setResourceProcessor(processorPID);
        return data;
    }

    public static ArtifactData createArtifact(URL url, String filename, String processorPID) {
        ArtifactData data = new ArtifactData(url, filename);
        data.setArtifactResourceProcessor(processorPID);
        return data;
    }

    /**
     * Some headers in OSGi allow for optional parameters, that are appended after the main value and always start with
     * a semicolon.
     *
     * @param name
     *            the name to remove the (optional) parameters from, cannot be <code>null</code>.
     * @return the cleaned name, never <code>null</code>.
     */
    private static String removeParameters(String name) {
        int idx = name.indexOf(';');
        if (idx > 0) {
            return name.substring(0, idx);
        }
        return name;
    }

    public URL getURL() {
        return m_url;
    }

    public boolean isBundle() {
        return m_isBundle;
    }

    public String getFilename() {
        return m_filename;
    }

    public String getSymbolicName() {
        return m_bundleSymbolicName;
    }

    public String getVersion() {
        return m_bundleVersion;
    }

    public boolean isCustomizer() {
        return m_isCustomizer;
    }

    public String getProcessorPid() {
        return m_processorPID;
    }

    private void setBundleMetadata(String bundleSymbolicName, String bundleVersion) {
        m_isBundle = true;
        // See OSGi spec, section 3.5.2 (v4.2)...
        m_bundleSymbolicName = removeParameters(bundleSymbolicName);
        m_bundleVersion = bundleVersion;
    }

    private void setResourceProcessor(String processorPID) {
        m_isCustomizer = true;
        // Not explicitly mentioned in the spec, but just to be sure...
        m_processorPID = removeParameters(processorPID);
    }

    private void setArtifactResourceProcessor(String processorPID) {
        // Not explicitly mentioned in the spec, but just to be sure...
        m_processorPID = removeParameters(processorPID);
    }
}
