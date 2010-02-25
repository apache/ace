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
package org.apache.ace.client.repository.helper.bundle.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.util.VersionRange;
import org.osgi.framework.Version;

/**
 * BundleHelperImpl provides the Artifact Repository with Helper and Recognizer services.
 */
public class BundleHelperImpl implements ArtifactRecognizer, BundleHelper {
    /** A custom <code>Comparator</code>, used to sort bundles in increasing version */
    private static final Comparator <ArtifactObject> BUNDLE_COMPARATOR = new Comparator<ArtifactObject>() {
        public int compare(ArtifactObject left, ArtifactObject right) {
            Version vLeft = new Version(left.getAttribute(BundleHelper.KEY_VERSION));
            Version vRight = new Version(right.getAttribute(BundleHelper.KEY_VERSION));
            return vRight.compareTo(vLeft);
        }
    };

    /*
     * From ArtifactHelper
     */
    public boolean canUse(ArtifactObject object) {
        if (object == null) {
            return false;
        }
        return (object.getMimetype().equals(MIMETYPE));
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        /*
         * Creates an endpoint filter for an association. If there is a KEY_ASSOCIATION_VERSIONSTATEMENT, a filter
         * will be created that matches exactly the given range.
         */
        if ((properties != null) && properties.containsKey(KEY_ASSOCIATION_VERSIONSTATEMENT)) {
            String versions = properties.get(KEY_ASSOCIATION_VERSIONSTATEMENT);
            VersionRange versionRange = null;
            try {
                versionRange = VersionRange.parse(versions);
            }
            catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("version " + ((versions != null) ? versions + " " : "(null) ") + "cannot be parsed into a valid version range statement.");
            }

            StringBuilder bundleStatement = new StringBuilder("(&(" + KEY_SYMBOLICNAME + "=" + RepositoryUtil.escapeFilterValue(obj.getAttribute(KEY_SYMBOLICNAME)) + ")");

            bundleStatement.append("(" + KEY_VERSION + ">=" + versionRange.getLow() + ")");
            if (!versionRange.isLowInclusive()) {
                bundleStatement.append("(!(" + KEY_VERSION + "=" + versionRange.getLow() + "))");
            }

            if (versionRange.getHigh() != null) {
                bundleStatement.append("(" + KEY_VERSION + "<=" + versionRange.getHigh() + ")");
                if (!versionRange.isHighInclusive()) {
                    bundleStatement.append("(!(" + KEY_VERSION + "=" + versionRange.getHigh() + "))");
                }
            }

            bundleStatement.append(")");

            return bundleStatement.toString();
        }
        else
        {
            if (obj.getAttribute(KEY_VERSION) != null) {
                return "(&(" + KEY_SYMBOLICNAME + "=" + RepositoryUtil.escapeFilterValue(obj.getAttribute(KEY_SYMBOLICNAME)) + ")(" + KEY_VERSION + "=" + RepositoryUtil.escapeFilterValue(obj.getAttribute(KEY_VERSION)) + "))";
            }
            else {
                return "(&(" + KEY_SYMBOLICNAME + "=" + RepositoryUtil.escapeFilterValue(obj.getAttribute(KEY_SYMBOLICNAME)) + ")(!(" + KEY_VERSION + "=*)))";
            }
        }
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        /* Normally, all objects that match the filter given by the previous version should be part of the
         * association. However, when a version statement has been given, only one should be used. */
        if ((properties != null) && properties.containsKey(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT)) {
            return 1;
        }
        else {
            return Integer.MAX_VALUE;
        }
    }

    public Comparator<ArtifactObject> getComparator() {
        return BUNDLE_COMPARATOR;
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        return normalizeVersion(attributes);
    }

    /**
     * For the filter to work correctly, we need to make sure the version statement is an
     * OSGi version.
     */
    private static Map<String, String> normalizeVersion(Map<String, String> input) {
        String version = input.get(KEY_VERSION);
        if (version != null) {
            try {
                Version theVersion = new Version(version);
                input.put(KEY_VERSION, theVersion.toString());
            }
            catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("The version statement in the bundle cannot be parsed to a valid version.", iae);
            }
        }

        return input;
    }

    /*
     * From BundleHelper
     */
    public String[] getDefiningKeys() {
        return new String[] {KEY_SYMBOLICNAME, KEY_VERSION};
    }

    public String[] getMandatoryAttributes() {
        return new String[] {KEY_SYMBOLICNAME};
    }

    public String getResourceProcessorPIDs(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_RESOURCE_PROCESSOR_PID);
    }

    public String getSymbolicName(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_SYMBOLICNAME);
    }

    public String getName(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_NAME);
    }

    public String getVersion(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_VERSION);
    }

    public String getVendor(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_VENDOR);
    }

    public boolean isResourceProcessor(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_RESOURCE_PROCESSOR_PID) != null;
    }

    private void ensureBundle(ArtifactObject object) {
        if ((object == null) || !object.getMimetype().equals(MIMETYPE)) {
            throw new IllegalArgumentException("This ArtifactObject cannot be handled by a BundleHelper.");
        }
    }

    /*
     * From ArtifactRecognizer
     */
    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public Map<String, String> extractMetaData(URL artifact) throws IllegalArgumentException {
        /*
         * Opens the URL as a Jar input stream, gets the manifest, and extracts headers from there.
         */
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(artifact.openStream());

            Attributes manifestAttributes = jis.getManifest().getMainAttributes();
            Map<String, String> result = new HashMap<String, String>();

            for (String key : new String[] {KEY_NAME, KEY_SYMBOLICNAME, KEY_VERSION, KEY_VENDOR, KEY_RESOURCE_PROCESSOR_PID}) {
                String value = manifestAttributes.getValue(key);
                if (value != null) {
                    result.put(key, value);
                }
            }

            if (result.get(KEY_VERSION) == null) {
                result.put(KEY_VERSION, "0.0.0");
            }

            result.put(ArtifactHelper.KEY_MIMETYPE, MIMETYPE);
            result.put(ArtifactObject.KEY_PROCESSOR_PID, "");
            String name = manifestAttributes.getValue(KEY_NAME);
            String version = manifestAttributes.getValue(KEY_VERSION);
            if (name == null) {
                name = manifestAttributes.getValue(KEY_SYMBOLICNAME);
            }
            result.put(ArtifactObject.KEY_ARTIFACT_NAME, name + (version == null ? "" : "-" + version));

            return result;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Error extracting metadata from artifact.", e);
        }
        finally {
            try {
                jis.close();
            }
            catch (IOException e) {
                // Too bad.
            }
        }
    }

    public String recognize(URL artifact) {
        /*
         * Tries to find out whether this artifact is a bundle by (a) trying to open it as a
         * jar, (b) trying to extract the manifest, and (c) checking whether that manifest
         * contains a Bundle-SymbolicName header.
         */
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(artifact.openStream());

            Manifest manifest = jis.getManifest();

            Attributes mainAttributes = manifest.getMainAttributes();
            if (mainAttributes.getValue(KEY_SYMBOLICNAME) != null) {
                return MIMETYPE;
            }
        }
        catch (Exception e) {
            return null;
        }
        finally {
            try {
                jis.close();
            }
            catch (Exception e) {
                // Too bad.
            }
        }
        return null;
    }

    public ArtifactPreprocessor getPreprocessor() {
        return null;
    }
}
