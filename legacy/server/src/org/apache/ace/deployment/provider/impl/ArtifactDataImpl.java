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
package org.apache.ace.deployment.provider.impl;

import java.net.URL;
import java.util.Map;
import java.util.jar.Attributes;

import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.deployment.provider.ArtifactData;
import org.osgi.framework.Constants;

/**
 * Implementation of <code>ArtifactData</code>. It overrides equals to make comparisons between versions easier.
 */
public class ArtifactDataImpl implements ArtifactData {
    public final static String HEADER_NAME = "Name";
    public static final String CUSTOMIZER = "DeploymentPackage-Customizer";
    public static final String PROCESSORPID = "Resource-Processor";

    private final String m_filename;
    private final String m_symbolicName;
    private final String m_version;
    private final Map<String, String> m_directives;

    private final URL m_url;
    private volatile boolean m_hasChanged;

    /**
     * Constructs an ArtifactDataImpl object.
     * @param url The URL to the bundle. It will also be used to create the filename.
     * The file-part of the url (after the last /) should It must only contain characters [A-Za-z0-9._-].
     * @param directives A map of extra directives.
     * @param symbolicName The symbolicname of the bundle.
     * @param version The version of the bundle. If this is <code>null</code> or empty, it will be
     * normalized to "0.0.0".
     * @param hasChanged Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(URL url, Map<String, String> directives, String symbolicName, String version, boolean hasChanged) {
        this(url, symbolicName, version, null, directives, hasChanged);
    }

    /**
     * Constructs an ArtifactDataImpl object.
     * @param url The URL to the bundle. It will also be used to create the filename.
     * The file-part of the url (after the last /) should It must only contain characters [A-Za-z0-9._-].
     * @param directives A map of extra directives.
     * @param hasChanged Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(URL url, Map<String, String> directives, boolean hasChanged) {
        this(url, null, null, null, directives, hasChanged);
    }

    /**
     * Constructs an ArtifactDataImpl object.
     * @param filename The filename of the bundle. If passed, it must only contain characters [A-Za-z0-9._-]; can be null.
     * @param symbolicName The symbolicname of the bundle.
     * @param version The version of the bundle. If this is <code>null</code> or empty, it will be
     * normalized to "0.0.0".
     * @param url The URL to the bundle. If filename is null, this will be used to create the filename; hence, the file-part of
     * the url (after the last /) should adhere to the same rules as filename.
     * @param hasChanged Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(String filename, String symbolicName, String version, URL url, boolean hasChanged) {
        this(url, symbolicName, version, filename, null, hasChanged);
    }

    private ArtifactDataImpl(URL url, String symbolicName, String version, String filename, Map<String, String> directives, boolean hasChanged) {
        m_url = url;

        if (filename != null) {
            m_filename = filename;
        }
        else {
            String urlString = m_url.toString();
            m_filename = (urlString == null) ? null : urlString.substring(urlString.lastIndexOf('/') + 1);
        }

        for (byte b : m_filename.getBytes()) {
            if (!(((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z')) || ((b >= '0') && (b <= '9')) || (b == '.') || (b == '-') || (b == '_'))) {
                throw new IllegalArgumentException("Filename " + m_filename + " " + (filename == null ? "constructed from the url" : "") + " contains an illegal character '" + new String(new byte[] {b}) + "'");
            }
        }

        m_symbolicName = symbolicName;
        if ((version == null) || (version.trim().length() == 0)) {
            m_version = "0.0.0";
        }
        else {
            m_version = version;
        }
        m_directives = directives;
        m_hasChanged = hasChanged;
    }

    public String getFilename() {
        return m_filename;
    }

    public String getSymbolicName() {
        return m_symbolicName;
    }

    public String getVersion() {
        return m_version;
    }

    public String getProcessorPid() {
        if (m_directives != null) {
            return m_directives.get(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID);
        }
        return null;
    }

    public URL getUrl() {
        return m_url;
    }

    public boolean hasChanged() {
        return m_hasChanged;
    }

    /**
     * @param hasChanged Indicate the bundle has changed
     */
    public void setChanged(boolean hasChanged) {
        m_hasChanged = hasChanged;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ArtifactDataImpl)) {
            return false;
        }
        ArtifactDataImpl jarFile2 = (ArtifactDataImpl) other;

        if (getSymbolicName() != null) {
            // this is a bundle
            return getSymbolicName().equals(jarFile2.getSymbolicName()) &&
            getVersion().equals(jarFile2.getVersion());
        }
        else {
            // this is another artifact.
            return m_url.equals(jarFile2.getUrl());
        }
    }

    @Override
    public int hashCode() {
        int result = 11;
        if (getSymbolicName() != null) {
            result = result ^ getSymbolicName().hashCode();
        }
        result = result ^ getVersion().hashCode();
        result = result ^ getUrl().hashCode();
        return result;
    }

    public Attributes getManifestAttributes(boolean fixPackage) {
        Attributes a = new Attributes();

        if (!isBundle()) {
            // this is a regular artifact
            a.putValue(PROCESSORPID, getProcessorPid());
        }
        else {
            a.putValue(Constants.BUNDLE_SYMBOLICNAME, getSymbolicName());
            a.putValue(Constants.BUNDLE_VERSION, getVersion());
            // this is a bundle
            if (isCustomizer()) {
                a.putValue(CUSTOMIZER, "true");
            }
        }

        if (!hasChanged() && fixPackage) {
            a.putValue("DeploymentPackage-Missing", "true");
        }

        return a;
    }

    public boolean isCustomizer() {
        return (m_directives != null) && "true".equals(m_directives.get(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER));
    }

    public boolean isBundle() {
        return getSymbolicName() != null;
    }

}
