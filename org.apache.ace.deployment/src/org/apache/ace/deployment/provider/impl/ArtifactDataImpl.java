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
import org.apache.ace.deployment.provider.ArtifactData;
import org.osgi.framework.Constants;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Implementation of <code>ArtifactData</code>. It overrides equals to make comparisons between versions easier.
 */
@ConsumerType
public class ArtifactDataImpl implements ArtifactData {
    public final static String HEADER_NAME = "Name";
    public static final String CUSTOMIZER = "DeploymentPackage-Customizer";
    public static final String PROCESSORPID = "Resource-Processor";

    /**
     * Key, intended to be used for artifacts which are bundles and will publish a resource processor (see OSGi
     * compendium section 114.10).
     */
    public static final String DIRECTIVE_ISCUSTOMIZER = "DeploymentPackage-Customizer";

    /**
     * Key, intended to be used for resources which require a resource processor (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_KEY_PROCESSORID = "Resource-Processor";

    /**
     * Key, intended to be used for artifacts which have a resourceID that's different from their generated name (based
     * on URL).
     */
    public static final String DIRECTIVE_KEY_RESOURCE_ID = "Resource-ID";

    /**
     * Key, intended to be used for matching processed (see ArtifactPreprocessor) to their 'original' one.
     */
    public static final String DIRECTIVE_KEY_BASEURL = "Base-Url";

    public static final String REPOSITORY_PATH = "ACE-RepositoryPath";

    private final String m_filename;
    private final String m_symbolicName;
    private final String m_version;
    private final long m_size;
    private final Map<String, String> m_directives;
    private final URL m_url;

    private volatile boolean m_hasChanged;

    /**
     * Constructs an ArtifactDataImpl object.
     * 
     * @param url
     *            The URL to the bundle. It will also be used to create the filename. The file-part of the url (after
     *            the last /) should It must only contain characters [A-Za-z0-9._-].
     * @param directives
     *            A map of extra directives.
     * @param symbolicName
     *            The symbolicname of the bundle.
     * @param size
     *            the (estimated) size of the artifact, or -1L if the size is unknown;
     * @param version
     *            The version of the bundle. If this is <code>null</code> or empty, it will be normalized to "0.0.0".
     * @param hasChanged
     *            Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(URL url, Map<String, String> directives, String symbolicName, long size, String version, boolean hasChanged) {
        this(url, symbolicName, size, version, null, directives, hasChanged);
    }

    /**
     * Constructs an ArtifactDataImpl object.
     * 
     * @param url
     *            The URL to the bundle. It will also be used to create the filename. The file-part of the url (after
     *            the last /) should It must only contain characters [A-Za-z0-9._-].
     * @param directives
     *            A map of extra directives;
     * @param filename
     *            the name of the artifact;
     * @param size
     *            the (estimated) size of the artifact, or -1L if the size is unknown;
     * @param hasChanged
     *            Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(URL url, Map<String, String> directives, String filename, long size, boolean hasChanged) {
        this(url, null, size, null, filename, directives, hasChanged);
    }

    /**
     * Constructs an ArtifactDataImpl object.
     * 
     * @param url
     *            The URL to the bundle. It will also be used to create the filename. The file-part of the url (after
     *            the last /) should It must only contain characters [A-Za-z0-9._-].
     * @param directives
     *            A map of extra directives.
     * @param hasChanged
     *            Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(URL url, Map<String, String> directives, boolean hasChanged) {
        this(url, null, -1L, null, null, directives, hasChanged);
    }

    /**
     * Constructs an ArtifactDataImpl object.
     * 
     * @param filename
     *            The filename of the bundle. If passed, it must only contain characters [A-Za-z0-9._-]; can be null.
     * @param symbolicName
     *            The symbolicname of the bundle.
     * @param version
     *            The version of the bundle. If this is <code>null</code> or empty, it will be normalized to "0.0.0".
     * @param url
     *            The URL to the bundle. If filename is null, this will be used to create the filename; hence, the
     *            file-part of the url (after the last /) should adhere to the same rules as filename.
     * @param hasChanged
     *            Indication of whether this bundle has changed relative to the previous deployment.
     */
    public ArtifactDataImpl(String filename, String symbolicName, long size, String version, URL url, boolean hasChanged) {
        this(url, symbolicName, size, version, filename, null, hasChanged);
    }

    private ArtifactDataImpl(URL url, String symbolicName, long size, String version, String filename, Map<String, String> directives, boolean hasChanged) {
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
                throw new IllegalArgumentException("Filename " + m_filename + " " + (filename == null ? "constructed from the url" : "") + " contains an illegal character '" + new String(new byte[] { b }) + "'");
            }
        }

        m_size = size;
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

    @Override
    public String getFilename() {
        return m_filename;
    }

    @Override
    public long getSize() {
        return m_size;
    }

    @Override
    public String getSymbolicName() {
        return m_symbolicName;
    }

    @Override
    public String getVersion() {
        return m_version;
    }

    @Override
    public String getProcessorPid() {
        if (m_directives != null) {
            return m_directives.get(DIRECTIVE_KEY_PROCESSORID);
        }
        return null;
    }

    @Override
    public URL getUrl() {
        return m_url;
    }

    @Override
    public boolean hasChanged() {
        return m_hasChanged;
    }

    /**
     * @param hasChanged
     *            Indicate the bundle has changed
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

    @Override
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

        if (m_directives != null) {
            String path = m_directives.get(REPOSITORY_PATH);
            if (path != null) {
                a.putValue(REPOSITORY_PATH, path);
            }
        }
        if (!hasChanged() && fixPackage) {
            a.putValue("DeploymentPackage-Missing", "true");
        }

        return a;
    }

    @Override
    public boolean isCustomizer() {
        return (m_directives != null) && "true".equals(m_directives.get(DIRECTIVE_ISCUSTOMIZER));
    }

    @Override
    public boolean isBundle() {
        return getSymbolicName() != null;
    }
}
