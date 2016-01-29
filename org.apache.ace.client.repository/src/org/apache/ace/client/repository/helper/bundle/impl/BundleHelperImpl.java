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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * BundleHelperImpl provides the Artifact Repository with Helper and Recognizer services.
 */
public class BundleHelperImpl implements ArtifactRecognizer, BundleHelper {

    // manifest headers that will be extracted as metadata
    private static final String[] MANIFEST_HEADERS = new String[] { KEY_NAME, KEY_SYMBOLICNAME, KEY_VERSION, KEY_VENDOR, KEY_RESOURCE_PROCESSOR_PID };

    // supported locales for manifest header localization in order of interest
    private static final Locale[] MANIFEST_LOCALIZATION_LOCALES = new Locale[] { Locale.US, Locale.ENGLISH, new Locale("nl") };

    /** A custom <code>Comparator</code>, used to sort bundles in increasing version */
    private static final Comparator<ArtifactObject> BUNDLE_COMPARATOR = new Comparator<ArtifactObject>() {
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
        return (MIMETYPE.equals(object.getMimetype()));
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        /*
         * Creates an endpoint filter for an association. If there is a KEY_ASSOCIATION_VERSIONSTATEMENT, a filter will
         * be created that matches exactly the given range.
         */

        String symbolicName = RepositoryUtil.escapeFilterValue(removeParameters(obj.getAttribute(KEY_SYMBOLICNAME)));
        if ((properties != null) && properties.containsKey(KEY_ASSOCIATION_VERSIONSTATEMENT)) {
            String versions = properties.get(KEY_ASSOCIATION_VERSIONSTATEMENT);
            VersionRange versionRange = null;
            try {
                versionRange = VersionRange.parse(versions);
            }
            catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("version " + ((versions != null) ? versions + " " : "(null) ") + "cannot be parsed into a valid version range statement.");
            }

            StringBuilder bundleStatement = new StringBuilder("(&(" + KEY_SYMBOLICNAME + "=" + symbolicName + ")");

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
            String version = obj.getAttribute(KEY_VERSION);
            if (version != null) {
                return "(&(" + KEY_SYMBOLICNAME + "=" + symbolicName + ")(" + KEY_VERSION + "=" + RepositoryUtil.escapeFilterValue(version) + "))";
            }
            else {
                return "(&(" + KEY_SYMBOLICNAME + "=" + symbolicName + ")(!(" + KEY_VERSION + "=*)))";
            }
        }
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        /*
         * Normally, all objects that match the filter given by the previous version should be part of the association.
         * However, when a version statement has been given, only one should be used.
         */
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
     * For the filter to work correctly, we need to make sure the version statement is an OSGi version.
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

    /*
     * From BundleHelper
     */
    public String[] getDefiningKeys() {
        return new String[] { KEY_SYMBOLICNAME, KEY_VERSION };
    }

    public String[] getMandatoryAttributes() {
        return new String[] { KEY_SYMBOLICNAME };
    }

    public String getResourceProcessorPIDs(ArtifactObject object) {
        ensureBundle(object);
        return object.getAttribute(KEY_RESOURCE_PROCESSOR_PID);
    }

    public String getSymbolicName(ArtifactObject object) {
        ensureBundle(object);
        return removeParameters(object.getAttribute(KEY_SYMBOLICNAME));
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

    public Map<String, String> extractMetaData(ArtifactResource artifact) throws IllegalArgumentException {
        try {
            Map<String, String> metadata = extractLocalizedHeaders(artifact);

            if (metadata.get(KEY_VERSION) == null) {
                metadata.put(KEY_VERSION, "0.0.0");
            }
            metadata.put(ArtifactHelper.KEY_MIMETYPE, MIMETYPE);
            metadata.put(ArtifactObject.KEY_PROCESSOR_PID, "");
            String name = metadata.get(KEY_NAME);
            String version = metadata.get(KEY_VERSION);
            if (name == null) {
                name = removeParameters(metadata.get(KEY_SYMBOLICNAME));
            }
            metadata.put(ArtifactObject.KEY_ARTIFACT_NAME, name + "-" + version);
            return metadata;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Error extracting metadata from artifact.", e);
        }
    }

    public String recognize(ArtifactResource artifact) {
        /*
         * Tries to find out whether this artifact is a bundle by (a) trying to open it as a jar, (b) trying to extract
         * the manifest, and (c) checking whether that manifest contains a Bundle-SymbolicName header.
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
                if (jis != null) {
                    jis.close();
                }
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

    public String getExtension(ArtifactResource artifact) {
        return ".jar";
    }

    /**
     * Extracts the {@link MANIFEST_HEADERS} from the manifest with support for localization (see OSGi core 3.11.2).
     * 
     * @param artifact
     *            the artifact
     * @return a map of localized headers
     * @throws IOException
     *             if reading from the stream fails
     */
    private Map<String, String> extractLocalizedHeaders(ArtifactResource artifact) throws IOException {

        Map<String, String> localizedHeaders = new HashMap<>();
        JarInputStream jarInputStream = null;
        try {
            jarInputStream = new JarInputStream(artifact.openStream());

            Manifest manifest = jarInputStream.getManifest();
            if (manifest == null) {
                throw new IOException("Failed to extract bundle manifest");
            }
            Attributes attributes = manifest.getMainAttributes();
            Properties localizationProperties = null;

            for (String key : MANIFEST_HEADERS) {
                String value = attributes.getValue(key);
                if (value == null) {
                    continue;
                }
                if (value.startsWith("%")) {
                    if (localizationProperties == null) {
                        // lazily instantiated because this is expensive and not widely used
                        localizationProperties = loadLocalizationProperties(jarInputStream, manifest);
                    }
                    value = localizationProperties.getProperty(value.substring(1), value);
                }
                if (KEY_SYMBOLICNAME.equals(key)) {
                    value = removeParameters(value);
                }
                localizedHeaders.put(key, value);
            }
            return localizedHeaders;
        }
        finally {
            try {
                if (jarInputStream != null) {
                    jarInputStream.close();
                }
            }
            catch (IOException e) {
            }
        }
    }

    /**
     * Searches a JarInputStream for localization entries considering {@link #MANIFEST_LOCALIZATION_LOCALES} and the
     * default.
     * 
     * @param jarInputStream
     *            the input, will not be closed
     * @param manifest
     *            the manifest
     * @return the matching localized values
     * @throws IOException
     *             if reading from the stream fails
     */
    private Properties loadLocalizationProperties(JarInputStream jarInputStream, Manifest manifest) throws IOException {

        Attributes attributes = manifest.getMainAttributes();

        String localizationBaseName = attributes.getValue(Constants.BUNDLE_LOCALIZATION);
        if (localizationBaseName == null) {
            localizationBaseName = Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME;
        }

        List<String> localeEntryNames = new ArrayList<>();
        for (Locale locale : MANIFEST_LOCALIZATION_LOCALES) {
            localeEntryNames.add(localizationBaseName + "_" + locale.toString() + ".properties");
        }
        localeEntryNames.add(localizationBaseName + ".properties");

        Properties localeProperties = new Properties();
        int currentLocaleEntryNameIndex = -1;

        JarEntry entry;
        while ((entry = jarInputStream.getNextJarEntry()) != null) {

            String entryName = entry.getName();
            int localeEntryNameIndex = localeEntryNames.indexOf(entryName);
            if (localeEntryNameIndex == -1 || (currentLocaleEntryNameIndex > -1 && localeEntryNameIndex > currentLocaleEntryNameIndex)) {
                // not a locale resource or we have already found a better matching one
                continue;
            }

            int b;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((b = jarInputStream.read()) != -1) {
                baos.write(b);
            }
            byte[] bytes = baos.toByteArray();
            baos.close();

            Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
            localeProperties.clear();
            localeProperties.load(reader);
            reader.close();

            currentLocaleEntryNameIndex = localeEntryNameIndex;
            if (localeEntryNameIndex == 0) {
                // found best match!
                break;
            }
        }
        return localeProperties;
    }
}
