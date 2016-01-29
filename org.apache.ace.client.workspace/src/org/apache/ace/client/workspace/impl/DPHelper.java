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
package org.apache.ace.client.workspace.impl;

import static org.apache.ace.client.workspace.Workspace.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.osgi.service.log.LogService;

/**
 * Utility class that allows a deployment package to be imported in a workspace in such way that it recreates all
 * artifacts, features, distributions and their associations.
 * <p>
 * This functionality uploads all artifacts from the given deployment package, and for each feature and distribution
 * will recreate(!) all associations. This means that associations that were made by hand are <b>not</b> preserved!<br>
 * In addition, no attempt is made to clean up artifacts that are no longer used (which is currently not possible with
 * the default OBR in ACE).
 * </p>
 */
class DPHelper {
    private static final Map<String, String> NO_TAGS = Collections.<String, String> emptyMap();

    private static final String ARTIFACT_NAME = "artifactName";
    private static final String ACE_REPOSITORY_PATH = "ACE-RepositoryPath";
    private static final String BUNDLE_VERSION = "Bundle-Version";
    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    private static final String DEPLOYMENT_PACKAGE_CUSTOMIZER = "DeploymentPackage-Customizer";
    private static final String DEPLOYMENT_PACKAGE_MISSING = "DeploymentPackage-Missing";

    private final WorkspaceImpl m_workspace;
    private final LogService m_log;

    /**
     * Creates a new DPHelper instance.
     */
    public DPHelper(WorkspaceImpl workspace, LogService log) {
        m_workspace = workspace;
        m_log = log;
    }

    public void importDeploymentPackage(String dpURL, boolean autoCommit) throws Exception {
        URL url = URI.create(dpURL).toURL();

        InputStream is = null;
        JarInputStream jis = null;

        try {
            is = url.openStream();
            jis = new JarInputStream(is);

            importDeploymentPackage(jis, autoCommit);
        }
        finally {
            closeSilently(is);
            closeSilently(jis);
        }
    }

    protected void importDeploymentPackage(JarInputStream is, boolean autoCommit) throws Exception {
        Map<ArtifactObject, FeatureObject> a2fMap = new HashMap<>();
        Map<FeatureObject, DistributionObject> f2dMap = new HashMap<>();

        // Upload all (new) artifact...
        JarEntry jarEntry;
        while ((jarEntry = is.getNextJarEntry()) != null) {
            String name = jarEntry.getName();
            Attributes attributes = jarEntry.getAttributes();

            // Create & upload artifact...
            ArtifactObject artifact = createArtifact(name, attributes, is);
            if (artifact == null) {
                throw new RuntimeException("Failed to import deployment package! Missing artifact: " + name + " in fix package which is also not present in the OBR!");
            }

            String repositoryPath = attributes.getValue(ACE_REPOSITORY_PATH);
            if (repositoryPath == null) {
                m_log.log(LogService.LOG_WARNING, String.format("No ACE-RepositoryPath attribute present for '%s'; not creating assocations...", name));
                continue;
            }
            String[] names = repositoryPath.split(";");

            // Create feature...
            FeatureObject feature = createFeature(names[0]);

            // Create distribution...
            DistributionObject distribution = createDistribution(names[1]);

            a2fMap.put(artifact, feature);
            f2dMap.put(feature, distribution);

            is.closeEntry();
        }

        // Pre-fill...
        for (Map.Entry<String, Attributes> entry : is.getManifest().getEntries().entrySet()) {
            String name = entry.getKey();
            Attributes attributes = entry.getValue();
            boolean missing = Boolean.parseBoolean(attributes.getValue(DEPLOYMENT_PACKAGE_MISSING));

            ArtifactObject artifact = findArtifact(name, attributes);
            if (artifact == null && missing) {
                throw new RuntimeException("Failed to import deployment package! Artifact: " + name + " is missing in fix package, but not present in the OBR!");
            }

            String repositoryPath = attributes.getValue(ACE_REPOSITORY_PATH);
            if (repositoryPath == null) {
                m_log.log(LogService.LOG_WARNING, String.format("No ACE-RepositoryPath attribute present for '%s'; not creating assocations...", name));
                continue;
            }
            String[] names = repositoryPath.split(";");

            // Create feature...
            FeatureObject feature = createFeature(names[0]);

            // Create distribution...
            DistributionObject distribution = createDistribution(names[1]);

            a2fMap.put(artifact, feature);
            f2dMap.put(feature, distribution);
        }

        // Remove existing associations for feature & distribution...
        for (FeatureObject feature : f2dMap.keySet()) {
            disassociateArtifactsFromFeature(feature);
        }
        for (DistributionObject distribution : f2dMap.values()) {
            disassociateFeaturesFromDistribution(distribution);
        }

        // Create associations...
        for (Map.Entry<ArtifactObject, FeatureObject> entry : a2fMap.entrySet()) {
            associateArtifactToFeature(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<FeatureObject, DistributionObject> entry : f2dMap.entrySet()) {
            associateFeatureToDistribution(entry.getKey(), entry.getValue());
        }

        if (autoCommit) {
            m_workspace.commit();
        }
    }

    private void associateArtifactToFeature(ArtifactObject artifact, FeatureObject feature) throws Exception {
        if (artifact.getAssociationsWith(feature).isEmpty()) {
            m_log.log(LogService.LOG_DEBUG, String.format("Creating assocation between artifact '%s' and feature '%s'...", artifact.getName(), feature.getName()));

            m_workspace.createAssocation(ARTIFACT2FEATURE, artifact.getAssociationFilter(null), feature.getAssociationFilter(null), "1", "1");
        }
    }

    private void associateFeatureToDistribution(FeatureObject feature, DistributionObject distribution) throws Exception {
        if (feature.getAssociationsWith(distribution).isEmpty()) {
            m_log.log(LogService.LOG_DEBUG, String.format("Creating assocation between feature '%s' and distribution '%s'...", feature.getName(), distribution.getName()));

            m_workspace.createAssocation(FEATURE2DISTRIBUTION, feature.getAssociationFilter(null), distribution.getAssociationFilter(null), "1", "1");
        }
    }

    private void closeSilently(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IOException exception) {
            m_log.log(LogService.LOG_DEBUG, "Failed to close resource!", exception);
        }
    }

    private ArtifactObject createArtifact(String name, Attributes attrs, InputStream is) throws Exception {
        // Check what we've got, if it already exists in our repository...
        ArtifactObject artifact = findArtifact(name, attrs);

        if (artifact != null) {
            return artifact;
        }
        else if (Boolean.parseBoolean(attrs.getValue(DEPLOYMENT_PACKAGE_MISSING))) {
            m_log.log(LogService.LOG_WARNING, String.format("Unable to create artifact '%s' as it is missing...", name));
            return null;
        }
        else {
            m_log.log(LogService.LOG_INFO, String.format("Creating artifact '%s'...", name));

            File file = storeArtifactContents(name, is);
            try {
                return m_workspace.createArtifact(file.toURI().toURL().toExternalForm(), true /* upload */);
            }
            finally {
                file.delete();
            }
        }
    }

    private DistributionObject createDistribution(String name) throws Exception {
        String keyName = DistributionObject.KEY_NAME;
        List<DistributionObject> dists = m_workspace.ld(String.format("(%s=%s)", keyName, name));
        if (dists.size() > 1) {
            throw new RuntimeException("Multiple distributions found for: " + name + "!");
        }
        else if (dists.size() == 1) {
            return dists.get(0);
        }
        return m_workspace.createDistribution(Collections.singletonMap(keyName, name), NO_TAGS);
    }

    private FeatureObject createFeature(String name) throws Exception {
        String keyName = FeatureObject.KEY_NAME;
        List<FeatureObject> feats = m_workspace.lf(String.format("(%s=%s)", keyName, name));
        if (feats.size() > 1) {
            throw new RuntimeException("Multiple features found for: " + name + "!");
        }
        else if (feats.size() == 1) {
            return feats.get(0);
        }
        return m_workspace.createFeature(Collections.singletonMap(keyName, name), NO_TAGS);
    }

    private void disassociateArtifactsFromFeature(FeatureObject feature) {
        for (ArtifactObject artifact : feature.getArtifacts()) {
            for (Artifact2FeatureAssociation association : artifact.getAssociationsWith(feature)) {
                m_workspace.da2f(association);
            }
        }
    }

    private void disassociateFeaturesFromDistribution(DistributionObject distribution) {
        for (FeatureObject feature : distribution.getFeatures()) {
            for (Feature2DistributionAssociation association : feature.getAssociationsWith(distribution)) {
                m_workspace.df2d(association);
            }
        }
    }

    private ArtifactObject findArtifact(String name, Attributes attrs) throws Exception {
        List<ArtifactObject> objs;

        String customizer = attrs.getValue(DEPLOYMENT_PACKAGE_CUSTOMIZER);
        String bsn = attrs.getValue(BUNDLE_SYMBOLIC_NAME);
        String version = attrs.getValue(BUNDLE_VERSION);

        if (bsn != null) {
            String filter = String.format("(&(%s=%s)(%s=%s))", BUNDLE_SYMBOLIC_NAME, bsn, BUNDLE_VERSION, version);
            if (customizer != null) {
                // Resource processor...
                objs = m_workspace.lrp(filter);
            }
            else {
                // Plain bundle...
                objs = m_workspace.la(filter);
            }
        }
        else {
            // Other artifact...
            objs = m_workspace.la(String.format("(%s=%s)", ARTIFACT_NAME, name));
        }

        if (objs.size() > 1) {
            throw new RuntimeException("Multiple artifacts found for: " + name + "!");
        }
        else if (objs.size() == 1) {
            return objs.get(0);
        }

        return null;
    }

    private File storeArtifactContents(String name, InputStream is) throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File tempFile = new File(tmpDir, name);
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        finally {
            closeSilently(fos);
        }

        return tempFile;
    }
}
