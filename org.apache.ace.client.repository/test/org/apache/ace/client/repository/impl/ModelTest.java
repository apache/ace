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
package org.apache.ace.client.repository.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.helper.bundle.impl.BundleHelperImpl;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test class for the object model used <code>org.apache.ace.client.repository</code>.
 */
public class ModelTest {

    private ArtifactRepositoryImpl m_artifactRepository;
    private FeatureRepositoryImpl m_featureRepository;
    private Artifact2FeatureAssociationRepositoryImpl m_artifact2FeatureRepository;
    private DistributionRepositoryImpl m_distributionRepository;
    private Feature2DistributionAssociationRepositoryImpl m_feature2DistributionRepository;
    private TargetRepositoryImpl m_targetRepository;
    private Distribution2TargetAssociationRepositoryImpl m_distribution2TargetRepository;
    private DeploymentVersionRepositoryImpl m_deploymentVersionRepository;
    private RepositoryAdminImpl m_repositoryAdmin;

    private BundleHelperImpl m_bundleHelper = new BundleHelperImpl();
    private BundleContext m_mockBundleContext;
    private ChangeNotifier m_mockChangeNotifier;

    /**
     * The artifact object can test functionality coming from RepositoryObjectImpl, and ArtifactRepository checks much
     * of ObjectRepositoryImpl.
     * 
     * @throws InvalidSyntaxException
     */
    @Test()
    public void testArtifactObjectAndRepository() throws InvalidSyntaxException {
        // Create a very simple artifact.
        ArtifactObject a = createBasicArtifactObject("myartifact", "1.0.0", "1");

        // Try to create an illegal one
        try {
            createBasicArtifactObject("");
            assert false : "Creating an artifact with an empty name is not allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        // Even though the artifact is not yet associated to a feature, try to get its features.
        List<FeatureObject> features = a.getFeatures();

        assert features.size() == 0 : "The artifact is not associated, so it should not return any features.";

        assert a.getAttribute(BundleHelper.KEY_SYMBOLICNAME).equals("myartifact") : "We should be able to read an attribute we just put in ourselves.";

        a.addTag("mytag", "myvalue");

        assert a.getTag("mytag").equals("myvalue") : "We should be able to read an attribute we just put in ourselves.";
        assert a.getTag(BundleHelper.KEY_SYMBOLICNAME) == null : "We should not find an attribute value when asking for a tag.";

        a.addTag(BundleHelper.KEY_SYMBOLICNAME, "mytagname");

        assert a.getTag(BundleHelper.KEY_SYMBOLICNAME).equals("mytagname") : "We can adds tags that have the same name as a artifact, but still return another value.";

        Dictionary<String, Object> dict = a.getDictionary();

        assert dict.get("mytag") == "myvalue" : "The dictionary of the object should contain all tags.";
        assert dict.get(BundleHelper.KEY_VERSION).equals("1.0.0") : "The dictionary of the object should contain all attributes; we found " + dict.get(BundleHelper.KEY_VERSION);
        String[] foundNames = (String[]) dict.get(BundleHelper.KEY_SYMBOLICNAME);
        assert foundNames.length == 2 : "For keys which are used both as a value and as a tag, we should get back both from the dictionary in an array.";
        assert (foundNames[0].equals("myartifact") && foundNames[1].equals("mytagname")) ||
            (foundNames[1].equals("myartifact") && foundNames[0].equals("mytagname")) : "The order is undefined, but we should find both the items we put in for '" + BundleHelper.KEY_SYMBOLICNAME + "'.";

        assert m_artifactRepository.get().size() == 1 : "The repository should contain exactly one artifact.";
        assert m_artifactRepository.get().get(0).equals(a) : "The repository should contain exactly our artifact.";

        ArtifactObject b2 = createBasicArtifactObject("myotherartifact", "1");

        assert m_artifactRepository.get(createLocalFilter("(" + BundleHelper.KEY_SYMBOLICNAME + "=myartifact)")).size() == 1 : "When filtering for our artifact, we should find only that.";
        assert m_artifactRepository.get(createLocalFilter("(" + BundleHelper.KEY_VERSION + "=1.0.0)")).size() == 2 : "When filtering for a version, we should find two artifacts.";

        try {
            createBasicArtifactObject("myartifact", "1.0.0");
            assert false : "Adding a artifact which is identical to one already in the repository should be illegal.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        try {
            b2.addAttribute("thenewattribute", "withsomevalue");
        }
        catch (UnsupportedOperationException uoe) {
            assert false : "Adding arbitrary attributes to a artifact object should be allowed.";
        }

        try {
            b2.addAttribute(BundleHelper.KEY_SYMBOLICNAME, "artifact.42");
            assert false : "Changing key attributes in a artifact should not be allowed.";
        }
        catch (UnsupportedOperationException uoe) {
            // expected
        }

        try {
            Map<String, String> attr = new HashMap<>();
            attr.put(BundleHelper.KEY_NAME, "mynewartifact");
            Map<String, String> tags = new HashMap<>();
            m_artifactRepository.create(attr, tags);
            assert false : "Creating a artifact without specifying all mandatory atttributes should be illegal.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        m_artifactRepository.remove(a);

        try {
            a.addTag("mytag", "myvalue");
            assert false : "Deleted objects are not allowed to be changed.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        assert m_artifactRepository.get().size() == 1 : "After removing our first artifact, the repository should contain one artifact.";
        assert m_artifactRepository.get().get(0).equals(b2) : "After removing our first artifact, the repository should contain only our second artifact.";
    }

    /**
     * Tests that we can create artifacts which contain a certain size (estimate). See ACE-384.
     */
    @Test()
    public void testArtifactObjectSize() {
        ArtifactObject artifactWithSize = createBasicArtifactObject("myartifact", "1.0.0", "10");
        assert artifactWithSize.getSize() == 10 : "The artifact did not have a valid size?!";

        ArtifactObject artifactWithoutSize = createBasicArtifactObject("artifactWithoutSize", "1.0.0", null);
        assert artifactWithoutSize.getSize() == -1L : "The artifact did have a size?!";

        ArtifactObject artifactWithInvalidSize = createBasicArtifactObject("artifactWithInvalidSize", "1.0.0", "xyz");
        assert artifactWithInvalidSize.getSize() == -1L : "The artifact did have a size?!";
    }

    /**
     * Tests the behavior when associating stuff, and removing associations.
     */
    @Test()
    public void testAssociations() {
        initializeRepositoryAdmin();
        // Create two, rather boring, artifacts.
        ArtifactObject b1 = createBasicArtifactObject("artifact1");
        ArtifactObject b2 = createBasicArtifactObject("artifact2");

        // Create three features.
        FeatureObject g1 = createBasicFeatureObject("feature1");
        FeatureObject g2 = createBasicFeatureObject("feature2");
        FeatureObject g3 = createBasicFeatureObject("feature3");

        // Create some associations.
        Artifact2FeatureAssociation b2g1 = m_artifact2FeatureRepository.create(b1, g2);
        assert b2g1 != null;
        Artifact2FeatureAssociation b2g2 = m_artifact2FeatureRepository.create(b2, g1);
        assert b2g2 != null;
        Artifact2FeatureAssociation b2g3 = m_artifact2FeatureRepository.create(b1, g3);
        assert b2g3 != null;
        Artifact2FeatureAssociation b2g4 = m_artifact2FeatureRepository.create(b2, g3);
        assert b2g4 != null;

        // Do some basic checks on the repositories.
        assert m_artifactRepository.get().size() == 2 : "We should have two artifacts in our repository; we found " + m_artifactRepository.get().size() + ".";
        assert m_featureRepository.get().size() == 3 : "We should have three features in our repository; we found " + m_featureRepository.get().size() + ".";
        assert m_artifact2FeatureRepository.get().size() == 4 : "We should have four associations in our repository; we found " + m_artifact2FeatureRepository.get().size() + ".";

        assert (b2g4.getLeft().size() == 1) && b2g4.getLeft().contains(b2) : "The left side of the fourth association should be artifact 2.";
        assert (b2g4.getRight().size() == 1) && b2g4.getRight().contains(g3) : "The right side of the fourth association should be feature 3.";

        // Check the wiring: what is wired to what?
        List<FeatureObject> b1features = b1.getFeatures();
        List<FeatureObject> b2features = b2.getFeatures();

        List<ArtifactObject> g1artifacts = g1.getArtifacts();
        List<ArtifactObject> g2artifacts = g2.getArtifacts();
        List<ArtifactObject> g3artifacts = g3.getArtifacts();
        List<DistributionObject> g1distributions = g1.getDistributions();
        List<DistributionObject> g2distributions = g2.getDistributions();
        List<DistributionObject> g3distributions = g3.getDistributions();

        assert g1distributions.size() == 0 : "Feature one should not have any associations to distributions; we found " + g1distributions.size() + ".";
        assert g2distributions.size() == 0 : "Feature two should not have any associations to distributions; we found " + g2distributions.size() + ".";
        assert g3distributions.size() == 0 : "Feature three should not have any associations to distributions; we found " + g3distributions.size() + ".";

        List<FeatureObject> b1expectedFeatures = new ArrayList<>();
        b1expectedFeatures.add(g2);
        b1expectedFeatures.add(g3);
        List<FeatureObject> b2expectedFeatures = new ArrayList<>();
        b2expectedFeatures.add(g1);
        b2expectedFeatures.add(g3);

        List<ArtifactObject> g1expectedArtifacts = new ArrayList<>();
        g1expectedArtifacts.add(b2);
        List<ArtifactObject> g2expectedArtifacts = new ArrayList<>();
        g2expectedArtifacts.add(b1);
        List<ArtifactObject> g3expectedArtifacts = new ArrayList<>();
        g3expectedArtifacts.add(b1);
        g3expectedArtifacts.add(b2);

        assert b1features.containsAll(b1expectedFeatures) && b1expectedFeatures.containsAll(b1features) : "b1 should be associated to exactly features 2 and 3.";
        assert b2features.containsAll(b2expectedFeatures) && b2expectedFeatures.containsAll(b2features) : "b2 should be associated to exactly features 1 and 3.";

        assert g1artifacts.containsAll(g1expectedArtifacts) && g1expectedArtifacts.containsAll(g1artifacts) : "g1 should be associated to exactly artifact 2.";
        assert g2artifacts.containsAll(g2expectedArtifacts) && g2expectedArtifacts.containsAll(g2artifacts) : "g2 should be associated to exactly artifact 1.";
        assert g3artifacts.containsAll(g3expectedArtifacts) && g3expectedArtifacts.containsAll(g3artifacts) : "g3 should be associated to exactly artifacts 1 and 2.";

        m_artifact2FeatureRepository.remove(b2g4);

        b1features = b1.getFeatures();
        b2features = b2.getFeatures();
        g1artifacts = g1.getArtifacts();
        g2artifacts = g2.getArtifacts();
        g3artifacts = g3.getArtifacts();

        b2expectedFeatures.remove(g3);
        g3expectedArtifacts.remove(b2);

        assert b1features.containsAll(b1expectedFeatures) && b1expectedFeatures.containsAll(b1features) : "b1 should be associated to exactly features 2 and 3.";
        assert b2features.containsAll(b2expectedFeatures) && b2expectedFeatures.containsAll(b2features) : "b2 should be associated to exactly feature 1.";

        assert g1artifacts.containsAll(g1expectedArtifacts) && g1expectedArtifacts.containsAll(g1artifacts) : "g1 should be associated to exactly artifact 2.";
        assert g2artifacts.containsAll(g2expectedArtifacts) && g2expectedArtifacts.containsAll(g2artifacts) : "g2 should be associated to exactly artifact 1.";
        assert g3artifacts.containsAll(g3expectedArtifacts) && g3expectedArtifacts.containsAll(g3artifacts) : "g3 should be associated to exactly artifact 1.";
    }

    @Test()
    public void testAssociationsWithCardinality() {
        ArtifactObject a1 = createBasicArtifactObject("a1");
        FeatureObject f1 = createBasicFeatureObject("f1");
        FeatureObject f2 = createBasicFeatureObject("f2");
        FeatureObject f3 = createBasicFeatureObject("f3");

        Map<String, String> props = new HashMap<>();
        props.put(Association.LEFT_ENDPOINT, "(" + BundleHelper.KEY_SYMBOLICNAME + "=a1)");
        props.put(Association.LEFT_CARDINALITY, "1");
        props.put(Association.RIGHT_ENDPOINT, "(" + FeatureObject.KEY_NAME + "=f*)");
        props.put(Association.RIGHT_CARDINALITY, "2");
        Map<String, String> tags = new HashMap<>();

        try {
            m_artifact2FeatureRepository.create(props, tags);
            assert false : "There are three matches for the feature, but we have a cardinality of 2; we should expect a NPE because no comparator is provided.";
        }
        catch (NullPointerException npe) {
            // expected
        }

        props.put(Association.RIGHT_CARDINALITY, "3");

        Artifact2FeatureAssociation bg = m_artifact2FeatureRepository.create(props, tags);
        assert bg != null : "Assocating artifact to feature failed?!";

        assert a1.getFeatures().size() == 3 : "The artifact should be associated to three features.";
        assert (f1.getArtifacts().size() == 1) && f1.getArtifacts().contains(a1) : "g1 should be associated to only b1.";
        assert (f2.getArtifacts().size() == 1) && f2.getArtifacts().contains(a1) : "g1 should be associated to only b1.";
        assert (f3.getArtifacts().size() == 1) && f3.getArtifacts().contains(a1) : "g1 should be associated to only b1.";
    }

    @Test()
    public void testAssociationsWithLists() {
        ArtifactObject b1 = createBasicArtifactObject("b1");
        ArtifactObject b2 = createBasicArtifactObject("b2");
        ArtifactObject b3 = createBasicArtifactObject("b3");
        FeatureObject g1 = createBasicFeatureObject("g1");
        FeatureObject g2 = createBasicFeatureObject("g2");
        FeatureObject g3 = createBasicFeatureObject("g3");

        List<ArtifactObject> artifacts = new ArrayList<>();
        artifacts.add(b1);
        artifacts.add(b2);
        List<FeatureObject> features = new ArrayList<>();
        features.add(g1);
        features.add(g3);

        Artifact2FeatureAssociation bg = m_artifact2FeatureRepository.create(artifacts, features);

        assert bg.getLeft().size() == 2 : "We expect two artifacts on the left side of the association.";
        assert bg.getRight().size() == 2 : "We expect two features on the right side of the association.";

        assert bg.getLeft().contains(b1) : "b1 should be on the left side of the association.";
        assert bg.getLeft().contains(b2) : "b2 should be on the left side of the association.";
        assert !bg.getLeft().contains(b3) : "b3 should not be on the left side of the association.";
        assert bg.getRight().contains(g1) : "g1 should be on the right side of the association.";
        assert !bg.getRight().contains(g2) : "g2 should not be on the right side of the association.";
        assert bg.getRight().contains(g3) : "g3 should be on the right side of the association.";

        List<FeatureObject> foundFeatures = b1.getFeatures();
        assert foundFeatures.size() == 2 : "b1 should be associated with two features.";
        assert foundFeatures.contains(g1) : "b1 should be associated with g1";
        assert !foundFeatures.contains(g2) : "b1 not should be associated with g2";
        assert foundFeatures.contains(g3) : "b1 should be associated with g3";

        foundFeatures = b3.getFeatures();
        assert foundFeatures.size() == 0 : "b3 should not be associated with any features.";

        List<ArtifactObject> foundArtifacts = g3.getArtifacts();
        assert foundArtifacts.size() == 2 : "g1 should be associated with two features.";
        assert foundArtifacts.contains(b1) : "g1 should be associated with b1";
        assert foundArtifacts.contains(b2) : "g1 should be associated with b2";
        assert !foundArtifacts.contains(b3) : "g1 should not be associated with b3";
    }

    @Test()
    public void testDeploymentRepository() {
        DeploymentVersionObject version11 = createBasicDeploymentVersionObject("target1", "1", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject version12 = createBasicDeploymentVersionObject("target1", "2", new String[] { "artifact3", "artifact4" });
        // Note the different order in adding the versions for target2.
        DeploymentVersionObject version22 = createBasicDeploymentVersionObject("target2", "2", new String[] { "artifactC", "artifactD" });
        DeploymentVersionObject version21 = createBasicDeploymentVersionObject("target2", "1", new String[] { "artifactA", "artifactB" });

        assert m_deploymentVersionRepository.getDeploymentVersions("NotMyTarget").size() == 0 : "The deployment repository should not return" +
            "any versions when we ask for a target that does not exist, but it returns " + m_deploymentVersionRepository.getDeploymentVersions("NotMyTarget").size();

        List<DeploymentVersionObject> for1 = m_deploymentVersionRepository.getDeploymentVersions("target1");
        assert for1.size() == 2 : "We expect two versions for target1, but we find " + for1.size();
        assert for1.get(0) == version11 : "The first version for target1 should be version11";
        assert for1.get(1) == version12 : "The second version for target1 should be version12";

        List<DeploymentVersionObject> for2 = m_deploymentVersionRepository.getDeploymentVersions("target2");
        assert for2.size() == 2 : "We expect two versions for target2, but we find " + for2.size();
        assert for2.get(0) == version21 : "The first version for target2 should be version21";
        assert for2.get(1) == version22 : "The second version for target2 should be version22";

        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("NotMyTarget") == null : "The most recent version for a non-existent target should not exist.";
        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("target1") == version12 : "The most recent version for target1 should be version12";
        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("target2") == version22 : "The most recent version for target2 should be version22";
    }

    @Test()
    public void testDeploymentRepositoryFilter() {

        String gwId = "\\ ( * ) target1)";
        DeploymentVersionObject version1 = createBasicDeploymentVersionObject(gwId, "1", new String[] { "artifact1", "artifact2" });

        List<DeploymentVersionObject> for1 = m_deploymentVersionRepository.getDeploymentVersions(gwId);
        assert for1.size() == 1 : "We expect one version for" + gwId + ", but we find " + for1.size();
        assert for1.get(0) == version1 : "The only version for" + gwId + "should be version1";
    }

    @Test()
    public void testDeploymentVersion() throws IOException {
        DeploymentVersionObject version = createBasicDeploymentVersionObject("target1", "1", new String[] { "artifact1", "artifact2" });

        assert version.getDeploymentArtifacts().length == 2 : "We expect to find two artifacts, but we find " + version.getDeploymentArtifacts().length;
        assert version.getDeploymentArtifacts()[0].getUrl().equals("artifact1");
        assert version.getDeploymentArtifacts()[1].getUrl().equals("artifact2");

        ((DeploymentArtifactImpl) version.getDeploymentArtifacts()[0]).addDirective("myDirective", "myValue");

        try {
            createBasicDeploymentVersionObject("target1", "1", new String[] { "artifact1", "artifact2" });
            assert false : "Creating a deployment version with a target and version that already exists should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        assert m_deploymentVersionRepository.get().size() == 1 : "The disallowed version should not be in the repository; we find " + m_deploymentVersionRepository.get().size();
        assert m_deploymentVersionRepository.get().get(0) == version : "Only our newly created version object should be in the repository.";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet deployment = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_deploymentVersionRepository }, null, "", true);
        new RepositorySerializer(deployment).toXML(buffer);
        initializeRepositoryAdmin();

        assert m_deploymentVersionRepository.get().size() == 0;

        deployment = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_deploymentVersionRepository }, null, "", true);
        new RepositorySerializer(deployment).fromXML(new ByteArrayInputStream(buffer.toByteArray()));

        assert m_deploymentVersionRepository.get().size() == 1 : "The disallowed version should not be in the repository.";
        assert m_deploymentVersionRepository.get().get(0).equals(version) : "Only our newly created version object should be in the repository.";

        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts().length == 2 : "We expect to find two artifacts, but we find " + m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts().length;
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getUrl().equals("artifact1");
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getKeys().length == 1 : "We expect to find one directive in the first artifact.";
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getDirective("myDirective").equals("myValue") : "The directive should be 'myValue'.";
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[1].getUrl().equals("artifact2");
    }

    /**
     * Not a full-fledged testcase, but a quick test of the correctness of the specified classes for distributions,
     * targets and their associations. In essence, this test 'touches' all code which uses generic code which has been
     * tested by TestAssociations.
     */
    @Test()
    public void testDistribution2TargetAssociations() {
        initializeRepositoryAdmin();
        DistributionObject d1 = createBasicDistributionObject("distribution1");
        TargetObject t1 = createBasicTargetObject("target1");
        m_distribution2TargetRepository.create(d1, t1);

        assert d1.getFeatures().size() == 0 : "Distribution 1 should not be associated with any features; it is associated with " + d1.getFeatures().size() + ".";
        assert d1.getTargets().size() == 1 : "Distribution 1 should be associated with exactly one target; it is associated with " + d1.getTargets().size() + ".";

        assert t1.getDistributions().size() == 1 : "Target 1 should be associated with exactly one distribution; it is associated with " + t1.getDistributions().size() + ".";
    }

    /**
     * Tests the correctness of the equals() in RepositoryObject.
     */
    @Test()
    public void testEquals() {
        List<ArtifactObject> artifacts = new ArrayList<>();
        artifacts.add(createBasicArtifactObject("artifact1"));
        artifacts.add(createBasicArtifactObject("artifact2"));
        artifacts.get(1).addTag("thetag", "thevalue");
        artifacts.add(createBasicArtifactObject("artifact3"));

        List<ArtifactObject> backupArtifacts = new ArrayList<>();
        backupArtifacts.addAll(artifacts);

        for (ArtifactObject b : backupArtifacts) {
            artifacts.remove(b);
        }

        assert artifacts.size() == 0 : "The artifacts list should be empty; if not, the ArtifactObject's equals() could be broken.";
    }

    /**
     * Not a full-fledged testcase, but a quick test of the correctness of the specified classes for features,
     * distributions and their associations. In essence, this test 'touches' all code which uses generic code which has
     * been tested by TestAssociations.
     */
    @Test()
    public void TestFeature2DistributionAssociations() {
        initializeRepositoryAdmin();
        FeatureObject f1 = createBasicFeatureObject("feature1");
        DistributionObject d1 = createBasicDistributionObject("distribution1");
        Feature2DistributionAssociation f2d1 = m_feature2DistributionRepository.create(f1, d1);

        assert (f2d1.getLeft().size() == 1) && f2d1.getLeft().contains(f1) : "Left side of the association should be our feature.";
        assert (f2d1.getRight().size() == 1) && f2d1.getRight().contains(d1) : "Right side of the association should be our distribution.";

        assert f1.getArtifacts().size() == 0 : "Feature 1 should not be associated with any artifacts; it is associated with " + f1.getArtifacts().size() + ".";
        assert f1.getDistributions().size() == 1 : "Feature 1 should be associated with exactly one distribution; it is associated with " + f1.getDistributions().size() + ".";

        assert d1.getFeatures().size() == 1 : "Distribution 1 should be associated with exactly one feature; it is associated with " + d1.getFeatures().size() + ".";
        assert d1.getTargets().size() == 0 : "Distribution 1 should not be associated with any targets; it is associated with " + d1.getTargets().size() + ".";
    }

    @Test()
    public void testGetAssociationsWith() {
        initializeRepositoryAdmin();
        ArtifactObject a1 = createBasicArtifactObject("artifact1");
        FeatureObject f1 = createBasicFeatureObject("feature1");
        Artifact2FeatureAssociation a2f1 = m_artifact2FeatureRepository.create(a1, f1);

        List<Artifact2FeatureAssociation> b1Associations = a1.getAssociationsWith(f1);
        List<Artifact2FeatureAssociation> g1Associations = f1.getAssociationsWith(a1);

        assert b1Associations.size() == 1 : "The artifact has exactly one association to the feature, but it shows " + b1Associations.size() + ".";
        assert b1Associations.get(0) == a2f1 : "The artifact's association should be the one we created.";

        assert g1Associations.size() == 1 : "The feature has exactly one association to the artifact.";
        assert g1Associations.get(0) == a2f1 : "The feature's association should be the one we created.";
    }

    @Test()
    public void testLimitedNumberOfDeploymentVersions() throws IOException {
        RepositoryConfigurationImpl repoConfig = new RepositoryConfigurationImpl();
        repoConfig.setDeploymentVersionLimit(3); // only keep the 3 most recent deployment versions...

        m_deploymentVersionRepository = new DeploymentVersionRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_deploymentVersionRepository, BundleContext.class, m_mockBundleContext);

        // Add several bundles, but not enough to get any deployment version purged...
        DeploymentVersionObject target1_v1 = createBasicDeploymentVersionObject("target1", "1", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target1_v2 = createBasicDeploymentVersionObject("target1", "2", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target1_v3 = createBasicDeploymentVersionObject("target1", "3", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target2_v1 = createBasicDeploymentVersionObject("target2", "1", new String[] { "artifact3", "artifact4" });
        DeploymentVersionObject target2_v2 = createBasicDeploymentVersionObject("target2", "2", new String[] { "artifact3", "artifact5" });

        List<DeploymentVersionObject> repo = m_deploymentVersionRepository.get();
        // All created deployment versions should be present...
        assertEquals(repo.size(), 5);
        assertTrue(repo.contains(target1_v1));
        assertTrue(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));

        // Add a new deployment version, which should cause the oldest (= version 1) of target1 to be purged...
        DeploymentVersionObject target1_v4 = createBasicDeploymentVersionObject("target1", "4", new String[] { "artifact1", "artifact2" });

        repo = m_deploymentVersionRepository.get();
        // Still 5 deployment versions, without version 1 of target1...
        assertEquals(repo.size(), 5);
        assertFalse(repo.contains(target1_v1));
        assertTrue(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target1_v4));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));

        // Add yet another deployment version, which should cause the oldest (= version 2) of target1 to be purged...
        DeploymentVersionObject target1_v5 = createBasicDeploymentVersionObject("target1", "5", new String[] { "artifact1", "artifact2" });

        repo = m_deploymentVersionRepository.get();
        // Still 5 deployment versions, without versions 1 & 2 of target1...
        assertEquals(repo.size(), 5);
        assertFalse(repo.contains(target1_v1));
        assertFalse(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target1_v4));
        assertTrue(repo.contains(target1_v5));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));
    }

    @Test()
    public void testModelFiltering() throws InvalidSyntaxException {
        initializeRepositoryAdmin();
        // Create an empty artifact repository.
        Map<String, String> attributes = new HashMap<>();
        attributes.put("myattribute", "theattribute");
        attributes.put("name", "attname");
        Map<String, String> tags = new HashMap<>();

        assert m_featureRepository != null : "Something has gone wrong injecting the feature repository.";
        FeatureObject g1 = m_featureRepository.create(attributes, tags);
        g1.addTag("mytag", "thetag");
        g1.addTag("name", "tagname");
        g1.addTag("difficult", ")diffi)c*ul\\t");

        assert m_featureRepository.get(createLocalFilter("(myattribute=*)")).size() == 1 : "There should be a myattribute in b1.";
        assert m_featureRepository.get(createLocalFilter("(myattribute=theattribute)")).size() == 1 : "There should be myattribute=theattribute in b1.";
        assert m_featureRepository.get(createLocalFilter("(myattribute=thetag)")).size() == 0 : "There should not be myattribute=thetag in b1.";
        assert m_featureRepository.get(createLocalFilter("(mytag=*)")).size() == 1 : "There should be a mytag in b1.";
        assert m_featureRepository.get(createLocalFilter("(mytag=thetag)")).size() == 1 : "There should be mytag=thetag in b1.";
        assert m_featureRepository.get(createLocalFilter("(mytag=theattribute)")).size() == 0 : "There should not be mytag=theattribute in b1.";

        assert m_featureRepository.get(createLocalFilter("(name=*)")).size() == 1 : "There should be a name parameter in b1.";
        assert m_featureRepository.get(createLocalFilter("(name=attname)")).size() == 1 : "There should be a name=attname in b1.";
        assert m_featureRepository.get(createLocalFilter("(name=tagname)")).size() == 1 : "There should be a name=tagname in b1.";
        assert m_featureRepository.get(createLocalFilter("(name=thetag)")).size() == 0 : "There should not be name=thetag in b1.";

        try {
            m_featureRepository.get(createLocalFilter("(difficult=)diffi)c*ul\\t"));
            assert false : "The non-escaped difficult string should raise an error.";
        }
        catch (InvalidSyntaxException ex) {
            // expected
        }
        assert m_featureRepository.get(createLocalFilter("(difficult=" + RepositoryUtil.escapeFilterValue(")diffi)c*ul\\t") + ")")).size() == 1 : "The 'difficult' string should be correctly escaped, and thus return exactly one match.";
    }

    @Test()
    public void testRepositorySerialization() throws IOException {
        createBasicArtifactObject("myartifact", "1");
        createBasicArtifactObject("myartifact", "2");

        // Write the store to a stream, reset the repository, and re-read it.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_artifactRepository, m_artifact2FeatureRepository, m_featureRepository }, null, "", true);
        new RepositorySerializer(store).toXML(buffer);
        initializeRepositoryAdmin();
        store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_artifactRepository, m_artifact2FeatureRepository, m_featureRepository }, null, "", true);
        new RepositorySerializer(store).fromXML(new ByteArrayInputStream(buffer.toByteArray()));

        assert m_artifactRepository.get().size() == 2 : "We expect to find 2 artifacts, but we find " + m_artifactRepository.get().size();
    }

    @Test()
    public void testSerialization() throws IOException {
        ArtifactObject b1 = createBasicArtifactObject("artifact1");
        ArtifactObject b2 = createBasicArtifactObject("artifact2");
        ArtifactObject b3 = createBasicArtifactObject("artifact3");

        FeatureObject g1 = createBasicFeatureObject("feature1");
        FeatureObject g2 = createBasicFeatureObject("feature2");

        m_artifact2FeatureRepository.create(b1, g1);
        m_artifact2FeatureRepository.create(b2, g2);
        m_artifact2FeatureRepository.create(b3, g2);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_artifactRepository, m_featureRepository, m_artifact2FeatureRepository }, null, "", true);
        new RepositorySerializer(store).toXML(buffer);
        initializeRepositoryAdmin();
        store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] { m_artifactRepository, m_featureRepository, m_artifact2FeatureRepository }, null, "", true);
        new RepositorySerializer(store).fromXML(new ByteArrayInputStream(buffer.toByteArray()));

        assert m_artifactRepository.get().size() == 3 : "We expect to find 3 artifacts, but we find " + m_artifactRepository.get().size();
        assert m_featureRepository.get().size() == 2 : "We expect to find 2 features, but we find " + m_featureRepository.get().size();
        assert m_artifact2FeatureRepository.get().size() == 3 : "We expect to find 3 associations, but we find " + m_artifact2FeatureRepository.get().size();
        assert b1.isAssociated(g1, FeatureObject.class) : "After serialization, b1 should still be associated with g1.";
        assert !b1.isAssociated(g2, FeatureObject.class) : "After serialization, b1 should not be associated with g1.";
        assert !b2.isAssociated(g1, FeatureObject.class) : "After serialization, b2 should not be associated with g2.";
        assert b2.isAssociated(g2, FeatureObject.class) : "After serialization, b2 should still be associated with g2.";
        assert !b3.isAssociated(g1, FeatureObject.class) : "After serialization, b3 should not be associated with g2.";
        assert b3.isAssociated(g2, FeatureObject.class) : "After serialization, b3 should still be associated with g2.";
    }

    @Test()
    public void testUnlimitedNumberOfDeploymentVersions() throws IOException {
        RepositoryConfiguration repoConfig = new RepositoryConfigurationImpl();

        m_deploymentVersionRepository = new DeploymentVersionRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_deploymentVersionRepository, BundleContext.class, m_mockBundleContext);

        DeploymentVersionObject target1_v1 = createBasicDeploymentVersionObject("target1", "1", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target1_v2 = createBasicDeploymentVersionObject("target1", "2", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target1_v3 = createBasicDeploymentVersionObject("target1", "3", new String[] { "artifact1", "artifact2" });
        DeploymentVersionObject target2_v1 = createBasicDeploymentVersionObject("target2", "1", new String[] { "artifact3", "artifact4" });
        DeploymentVersionObject target2_v2 = createBasicDeploymentVersionObject("target2", "2", new String[] { "artifact3", "artifact5" });

        List<DeploymentVersionObject> repo = m_deploymentVersionRepository.get();
        assertEquals(repo.size(), 5);
        assertTrue(repo.contains(target1_v1));
        assertTrue(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));

        DeploymentVersionObject target1_v4 = createBasicDeploymentVersionObject("target1", "4", new String[] { "artifact1", "artifact2" });

        repo = m_deploymentVersionRepository.get();
        assertEquals(repo.size(), 6);
        assertTrue(repo.contains(target1_v1));
        assertTrue(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target1_v4));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));

        DeploymentVersionObject target1_v5 = createBasicDeploymentVersionObject("target1", "5", new String[] { "artifact1", "artifact2" });

        repo = m_deploymentVersionRepository.get();
        assertEquals(repo.size(), 7);
        assertTrue(repo.contains(target1_v1));
        assertTrue(repo.contains(target1_v2));
        assertTrue(repo.contains(target1_v3));
        assertTrue(repo.contains(target1_v4));
        assertTrue(repo.contains(target1_v5));
        assertTrue(repo.contains(target2_v1));
        assertTrue(repo.contains(target2_v2));
    }
    
    @Test()
    public void testConcurrentAccessToObjectRepository() throws Exception {
        initializeRepositoryAdmin();
        // adds 10 features
        for (int i = 0; i < 10; i++) {
            createBasicFeatureObject("feature-" + i);
        }
        // asks for a list
        List<FeatureObject> list = m_featureRepository.get();
        // then adds another feature
        createBasicFeatureObject("feature-X");
        // and makes sure the returned list still has 10 features, not 11, and not
        // throwing any concurrent modification exceptions
        assertEquals(list.size(), 10);
        Iterator<FeatureObject> iterator = list.iterator();
        for (int i = 0; i < 10; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.next() != null);
        }
        assertFalse(iterator.hasNext());
        // remove item from the list
        list.remove(0);
        // get a new list, ensure there are still 11 features
        list = m_featureRepository.get();
        assertEquals(list.size(), 11);
    }

    private ArtifactObject createBasicArtifactObject(String symbolicName) {
        return createBasicArtifactObject(symbolicName, null);
    }

    private ArtifactObject createBasicArtifactObject(String symbolicName, String version) {
        return createBasicArtifactObject(symbolicName, version, null);
    }

    private ArtifactObject createBasicArtifactObject(String symbolicName, String version, String size) {
        Map<String, String> attr = new HashMap<>();
        attr.put(BundleHelper.KEY_SYMBOLICNAME, symbolicName);
        attr.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        attr.put(ArtifactObject.KEY_URL, "http://" + symbolicName + "-v" + ((version == null) ? "null" : version));
        if (size != null) {
            attr.put(ArtifactObject.KEY_SIZE, size); // bytes
        }
        Map<String, String> tags = new HashMap<>();
        if (version != null) {
            attr.put(BundleHelper.KEY_VERSION, version);
        }
        return m_artifactRepository.create(attr, tags);
    }

    private DeploymentVersionObject createBasicDeploymentVersionObject(String targetID, String version, String[] artifacts) {
        Map<String, String> attr = new HashMap<>();
        attr.put(DeploymentVersionObject.KEY_TARGETID, targetID);
        attr.put(DeploymentVersionObject.KEY_VERSION, version);
        Map<String, String> tags = new HashMap<>();

        List<DeploymentArtifactImpl> deploymentArtifacts = new ArrayList<>();
        for (String s : artifacts) {
            deploymentArtifacts.add(new DeploymentArtifactImpl(s, -1L));
        }
        return m_deploymentVersionRepository.create(attr, tags, deploymentArtifacts.toArray(new DeploymentArtifact[0]));
    }

    private DistributionObject createBasicDistributionObject(String name) {
        Map<String, String> attr = new HashMap<>();
        attr.put(DistributionObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<>();

        return m_distributionRepository.create(attr, tags);
    }

    private FeatureObject createBasicFeatureObject(String name) {
        Map<String, String> attr = new HashMap<>();
        attr.put(FeatureObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<>();

        return m_featureRepository.create(attr, tags);
    }

    private TargetObject createBasicTargetObject(String id) {
        Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, id);
        Map<String, String> tags = new HashMap<>();

        return m_targetRepository.create(attr, tags);
    }

    private Filter createLocalFilter(String filter) throws InvalidSyntaxException {
        return FrameworkUtil.createFilter(filter);
    }

    @BeforeMethod(alwaysRun = true)
    private void initializeRepositoryAdmin() {
        m_mockBundleContext = TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public Filter createFilter(String filter) throws InvalidSyntaxException {
                return createLocalFilter(filter);
            }
        });

        m_mockChangeNotifier = TestUtils.createNullObject(ChangeNotifier.class);

        RepositoryConfiguration repoConfig = new RepositoryConfigurationImpl();

        m_artifactRepository = new ArtifactRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_artifactRepository, LogService.class);
        TestUtils.configureObject(m_artifactRepository, BundleContext.class, m_mockBundleContext);
        m_artifactRepository.addHelper(BundleHelper.MIMETYPE, m_bundleHelper);
        m_featureRepository = new FeatureRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_featureRepository, BundleContext.class, m_mockBundleContext);
        m_artifact2FeatureRepository = new Artifact2FeatureAssociationRepositoryImpl(m_artifactRepository, m_featureRepository, m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_artifact2FeatureRepository, BundleContext.class, m_mockBundleContext);
        m_distributionRepository = new DistributionRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_distributionRepository, BundleContext.class, m_mockBundleContext);
        m_feature2DistributionRepository = new Feature2DistributionAssociationRepositoryImpl(m_featureRepository, m_distributionRepository, m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_feature2DistributionRepository, BundleContext.class, m_mockBundleContext);
        m_targetRepository = new TargetRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_targetRepository, BundleContext.class, m_mockBundleContext);
        m_distribution2TargetRepository = new Distribution2TargetAssociationRepositoryImpl(m_distributionRepository, m_targetRepository, m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_distribution2TargetRepository, BundleContext.class, m_mockBundleContext);
        m_deploymentVersionRepository = new DeploymentVersionRepositoryImpl(m_mockChangeNotifier, repoConfig);
        TestUtils.configureObject(m_deploymentVersionRepository, BundleContext.class, m_mockBundleContext);

        m_repositoryAdmin = new RepositoryAdminImpl("testSessionID", repoConfig);

        Map<Class<? extends ObjectRepository<?>>, ObjectRepositoryImpl<?, ?>> repos = new HashMap<>();
        repos.put(ArtifactRepository.class, m_artifactRepository);
        repos.put(Artifact2FeatureAssociationRepository.class, m_artifact2FeatureRepository);
        repos.put(FeatureRepository.class, m_featureRepository);
        repos.put(Feature2DistributionAssociationRepository.class, m_feature2DistributionRepository);
        repos.put(DistributionRepository.class, m_distributionRepository);
        repos.put(Distribution2TargetAssociationRepository.class, m_distribution2TargetRepository);
        repos.put(TargetRepository.class, m_targetRepository);
        repos.put(DeploymentVersionRepository.class, m_deploymentVersionRepository);

        m_repositoryAdmin.initialize(repos);
        TestUtils.configureObject(m_repositoryAdmin, Preferences.class);
        TestUtils.configureObject(m_repositoryAdmin, PreferencesService.class);
    }
}
