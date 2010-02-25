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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.helper.bundle.impl.BundleHelperImpl;
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


/**
 * Test class for the object model used </code>org.apache.ace.client.repository<code>.
 */
public class ModelTest {

    private ArtifactRepositoryImpl m_artifactRepository;
    private GroupRepositoryImpl m_groupRepository;
    private Artifact2GroupAssociationRepositoryImpl m_artifact2groupRepository;
    private LicenseRepositoryImpl m_licenseRepository;
    private Group2LicenseAssociationRepositoryImpl m_group2licenseRepository;
    private GatewayRepositoryImpl m_gatewayRepository;
    private License2GatewayAssociationRepositoryImpl m_license2gatewayRepository;
    private DeploymentVersionRepositoryImpl m_deploymentVersionRepository;
    private RepositoryAdminImpl m_repositoryAdmin;

    private BundleHelperImpl m_bundleHelper = new BundleHelperImpl();


    @BeforeMethod(alwaysRun = true)
    private void initializeRepositoryAdmin() {
        BundleContext bc = TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public Filter createFilter(String filter) throws InvalidSyntaxException {
                return createLocalFilter(filter);
            }
        });

        ChangeNotifier notifier = TestUtils.createNullObject(ChangeNotifier.class);

        m_artifactRepository = new ArtifactRepositoryImpl(notifier);
        TestUtils.configureObject(m_artifactRepository, LogService.class);
        TestUtils.configureObject(m_artifactRepository, BundleContext.class, bc);
        m_artifactRepository.addHelper(BundleHelper.MIMETYPE, m_bundleHelper);
        m_groupRepository = new GroupRepositoryImpl(notifier);
        TestUtils.configureObject(m_groupRepository, BundleContext.class, bc);
        m_artifact2groupRepository = new Artifact2GroupAssociationRepositoryImpl(m_artifactRepository, m_groupRepository, notifier);
        TestUtils.configureObject(m_artifact2groupRepository, BundleContext.class, bc);
        m_licenseRepository = new LicenseRepositoryImpl(notifier);
        TestUtils.configureObject(m_licenseRepository, BundleContext.class, bc);
        m_group2licenseRepository = new Group2LicenseAssociationRepositoryImpl(m_groupRepository, m_licenseRepository, notifier);
        TestUtils.configureObject(m_group2licenseRepository, BundleContext.class, bc);
        m_gatewayRepository = new GatewayRepositoryImpl(notifier);
        TestUtils.configureObject(m_gatewayRepository, BundleContext.class, bc);
        m_license2gatewayRepository = new License2GatewayAssociationRepositoryImpl(m_licenseRepository, m_gatewayRepository, notifier);
        TestUtils.configureObject(m_license2gatewayRepository, BundleContext.class, bc);
        m_deploymentVersionRepository = new DeploymentVersionRepositoryImpl(notifier);
        TestUtils.configureObject(m_deploymentVersionRepository, BundleContext.class, bc);

        m_repositoryAdmin = new RepositoryAdminImpl("testSessionID");

        Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> repos = new HashMap<Class<? extends ObjectRepository>, ObjectRepositoryImpl>();
        repos.put(ArtifactRepository.class, m_artifactRepository);
        repos.put(Artifact2GroupAssociationRepository.class, m_artifact2groupRepository);
        repos.put(GroupRepository.class, m_groupRepository);
        repos.put(Group2LicenseAssociationRepository.class, m_group2licenseRepository);
        repos.put(LicenseRepository.class, m_licenseRepository);
        repos.put(License2GatewayAssociationRepository.class, m_license2gatewayRepository);
        repos.put(GatewayRepository.class, m_gatewayRepository);
        repos.put(DeploymentVersionRepository.class, m_deploymentVersionRepository);

        m_repositoryAdmin.initialize(repos);
        TestUtils.configureObject(m_repositoryAdmin, Preferences.class);
        TestUtils.configureObject(m_repositoryAdmin, PreferencesService.class);
    }

    /**
     * The bundle object can test functionality coming from
     * RepositoryObjectImpl, and ArtifactRepository checks much of
     * ObjectRepositoryImpl.
     * @throws InvalidSyntaxException
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testBundleObjectAndRepository() throws InvalidSyntaxException {
        // Create a very simple bundle.
        ArtifactObject b = createBasicBundleObject("mybundle", "1.0.0");

        // Try to create an illegal one
        try {
            createBasicBundleObject("");
            assert false : "Creating a bundle with an empty name is not allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        // Even though the bundle is not yet associated to a group, try to get its groups.
        List<GroupObject> groups = b.getGroups();

        assert groups.size() == 0 : "The bundle is not associated, so it should not return any groups.";

        assert b.getAttribute(BundleHelper.KEY_SYMBOLICNAME).equals("mybundle") : "We should be able to read an attribute we just put in ourselves.";

        b.addTag("mytag", "myvalue");

        assert b.getTag("mytag").equals("myvalue")  : "We should be able to read an attribute we just put in ourselves.";
        assert b.getTag(BundleHelper.KEY_SYMBOLICNAME) == null : "We should not find an attribute value when asking for a tag.";

        b.addTag(BundleHelper.KEY_SYMBOLICNAME, "mytagname");

        assert b.getTag(BundleHelper.KEY_SYMBOLICNAME).equals("mytagname") : "We can adds tags that have the same name as a bundle, but still return another value.";

        Dictionary<String, Object> dict = b.getDictionary();

        assert dict.get("mytag") == "myvalue" : "The dictionary of the object should contain all tags.";
        assert dict.get(BundleHelper.KEY_VERSION).equals("1.0.0") : "The dictionary of the object should contain all attributes; we found " + dict.get(BundleHelper.KEY_VERSION);
        String[] foundNames = (String[]) dict.get(BundleHelper.KEY_SYMBOLICNAME);
        assert foundNames.length == 2 : "For keys which are used both as a value and as a tag, we should get back both from the dictionary in an array.";
        assert (foundNames[0].equals("mybundle") && foundNames[1].equals("mytagname")) ||
        (foundNames[1].equals("mybundle") && foundNames[0].equals("mytagname")) : "The order is undefined, but we should find both the items we put in for '"+BundleHelper.KEY_SYMBOLICNAME+"'.";

        assert m_artifactRepository.get().size() == 1 : "The repository should contain exactly one bundle.";
        assert m_artifactRepository.get().get(0).equals(b) : "The repository should contain exactly our bundle.";

        ArtifactObject b2 = createBasicBundleObject("myotherbundle", "1");

        assert m_artifactRepository.get(createLocalFilter("(" + BundleHelper.KEY_SYMBOLICNAME + "=mybundle)")).size() == 1 : "When filtering for our bundle, we should find only that.";
        assert m_artifactRepository.get(createLocalFilter("(" + BundleHelper.KEY_VERSION + "=1.0.0)")).size() == 2 : "When filtering for a version, we should find two bundles.";

        try {
            createBasicBundleObject("mybundle", "1.0.0");
            assert false : "Adding a bundle which is identical to one already in the repository should be illegal.";
        }
        catch (IllegalArgumentException iae) {
            //expected
        }

        try {
            b2.addAttribute("thenewattribute", "withsomevalue");
        }
        catch (UnsupportedOperationException uoe) {
            assert false : "Adding arbitrary attributes to a bundle object should be allowed.";
        }

        try {
            b2.addAttribute(BundleHelper.KEY_SYMBOLICNAME, "bundle.42");
            assert false : "Changing key attributes in a bundle should not be allowed.";
        }
        catch (UnsupportedOperationException uoe) {
            //expected
        }


        try {
            Map<String, String> attr = new HashMap<String, String>();
            attr.put(BundleHelper.KEY_NAME, "mynewbundle");
            Map<String, String> tags = new HashMap<String, String>();
            m_artifactRepository.create(attr, tags);
            assert false : "Creating a bundle without specifying all mandatory atttributes should be illegal.";
        }
        catch (IllegalArgumentException iae) {
            //expected
        }


        m_artifactRepository.remove(b);

        try {
            b.addTag("mytag", "myvalue");
            assert false : "Deleted objects are not allowed to be changed.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        assert m_artifactRepository.get().size() == 1 : "After removing our first bundle, the repository should contain one bundle.";
        assert m_artifactRepository.get().get(0).equals(b2) : "After removing our first bundle, the repository should contain only our second bundle.";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testRepositorySerialization() {
        createBasicBundleObject("mybundle", "1");
        createBasicBundleObject("mybundle", "2");

        // Write the store to a stream, reset the repository, and re-read it.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_artifactRepository, m_artifact2groupRepository, m_groupRepository}, null, "", true);
        new RepositorySerializer(store).toXML(buffer);
        initializeRepositoryAdmin();
        store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_artifactRepository, m_artifact2groupRepository, m_groupRepository}, null, "", true);
        new RepositorySerializer(store).fromXML(new ByteArrayInputStream(buffer.toByteArray()));

        assert m_artifactRepository.get().size() == 2 : "We expect to find 2 bundles, but we find " + m_artifactRepository.get().size();
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testSerialization() {
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        ArtifactObject b2 = createBasicBundleObject("bundle2");
        ArtifactObject b3 = createBasicBundleObject("bundle3");

        GroupObject g1 = createBasicGroupObject("group1");
        GroupObject g2 = createBasicGroupObject("group2");

        m_artifact2groupRepository.create(b1, g1);
        m_artifact2groupRepository.create(b2, g2);
        m_artifact2groupRepository.create(b3, g2);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_artifactRepository, m_groupRepository, m_artifact2groupRepository}, null, "", true);
        new RepositorySerializer(store).toXML(buffer);
        initializeRepositoryAdmin();
        store = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_artifactRepository, m_groupRepository, m_artifact2groupRepository}, null, "", true);
        new RepositorySerializer(store).fromXML(new ByteArrayInputStream(buffer.toByteArray()));

        assert m_artifactRepository.get().size() == 3 : "We expect to find 3 bundles, but we find " + m_artifactRepository.get().size();
        assert m_groupRepository.get().size() == 2 : "We expect to find 2 groups, but we find " + m_groupRepository.get().size();
        assert m_artifact2groupRepository.get().size() == 3 : "We expect to find 3 associations, but we find " + m_artifact2groupRepository.get().size();
        assert b1.isAssociated(g1, GroupObject.class) : "After serialization, b1 should still be associated with g1.";
        assert !b1.isAssociated(g2, GroupObject.class) : "After serialization, b1 should not be associated with g1.";
        assert !b2.isAssociated(g1, GroupObject.class) : "After serialization, b2 should not be associated with g2.";
        assert b2.isAssociated(g2, GroupObject.class) : "After serialization, b2 should still be associated with g2.";
        assert !b3.isAssociated(g1, GroupObject.class) : "After serialization, b3 should not be associated with g2.";
        assert b3.isAssociated(g2, GroupObject.class) : "After serialization, b3 should still be associated with g2.";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testModelFiltering() throws InvalidSyntaxException {
        initializeRepositoryAdmin();
        // Create an empty bundle repository.
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("myattribute", "theattribute");
        attributes.put("name", "attname");
        Map<String, String> tags = new HashMap<String, String>();

        assert m_groupRepository != null : "Something has gone wrong injecting the bundle repository.";
        GroupObject g1 = m_groupRepository.create(attributes, tags);
        g1.addTag("mytag", "thetag");
        g1.addTag("name", "tagname");
        g1.addTag("difficult", ")diffi)c*ul\\t");


        assert m_groupRepository.get(createLocalFilter("(myattribute=*)")).size() == 1 : "There should be a myattribute in b1.";
        assert m_groupRepository.get(createLocalFilter("(myattribute=theattribute)")).size() == 1 : "There should be myattribute=theattribute in b1.";
        assert m_groupRepository.get(createLocalFilter("(myattribute=thetag)")).size() == 0 : "There should not be myattribute=thetag in b1.";
        assert m_groupRepository.get(createLocalFilter("(mytag=*)")).size() == 1 : "There should be a mytag in b1.";
        assert m_groupRepository.get(createLocalFilter("(mytag=thetag)")).size() == 1 : "There should be mytag=thetag in b1.";
        assert m_groupRepository.get(createLocalFilter("(mytag=theattribute)")).size() == 0 : "There should not be mytag=theattribute in b1.";

        assert m_groupRepository.get(createLocalFilter("(name=*)")).size() == 1 : "There should be a name parameter in b1.";
        assert m_groupRepository.get(createLocalFilter("(name=attname)")).size() == 1 : "There should be a name=attname in b1.";
        assert m_groupRepository.get(createLocalFilter("(name=tagname)")).size() == 1 : "There should be a name=tagname in b1.";
        assert m_groupRepository.get(createLocalFilter("(name=thetag)")).size() == 0 : "There should not be name=thetag in b1.";

        try {
            m_groupRepository.get(createLocalFilter("(difficult=)diffi)c*ul\\t"));
            assert false : "The non-escaped difficult string should raise an error.";
        }
        catch (InvalidSyntaxException ex) {
            //expected
        }
        assert m_groupRepository.get(createLocalFilter("(difficult=" + RepositoryUtil.escapeFilterValue(")diffi)c*ul\\t") + ")")).size() == 1 : "The 'difficult' string should be correctly escaped, and thus return exactly one match.";
    }

    /**
     * Tests the behavior when associating stuff, and removing associations.
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testAssociations() {
        initializeRepositoryAdmin();
        // Create two, rather boring, bundles.
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        ArtifactObject b2 = createBasicBundleObject("bundle2");

        // Create three groups.
        GroupObject g1 = createBasicGroupObject("group1");
        GroupObject g2 = createBasicGroupObject("group2");
        GroupObject g3 = createBasicGroupObject("group3");

        // Create some associations.
        Artifact2GroupAssociation b2g1 = m_artifact2groupRepository.create(b1, g2);
        assert b2g1 != null;
        Artifact2GroupAssociation b2g2 = m_artifact2groupRepository.create(b2, g1);
        assert b2g2 != null;
        Artifact2GroupAssociation b2g3 = m_artifact2groupRepository.create(b1, g3);
        assert b2g3 != null;
        Artifact2GroupAssociation b2g4 = m_artifact2groupRepository.create(b2, g3);
        assert b2g4 != null;

        // Do some basic checks on the repositories.
        assert m_artifactRepository.get().size() == 2 : "We should have two bundles in our repository; we found " + m_artifactRepository.get().size() + ".";
        assert m_groupRepository.get().size() == 3 : "We should have three groups in our repository; we found " + m_groupRepository.get().size() + ".";
        assert m_artifact2groupRepository.get().size() == 4 : "We should have four associations in our repository; we found " + m_artifact2groupRepository.get().size() + ".";

        assert (b2g4.getLeft().size() == 1) && b2g4.getLeft().contains(b2) : "The left side of the fourth association should be bundle 2.";
        assert (b2g4.getRight().size() == 1) && b2g4.getRight().contains(g3) : "The right side of the fourth association should be group 3.";

        // Check the wiring: what is wired to what?
        List<GroupObject> b1groups = b1.getGroups();
        List<GroupObject> b2groups = b2.getGroups();

        List<ArtifactObject> g1bundles = g1.getArtifacts();
        List<ArtifactObject> g2bundles = g2.getArtifacts();
        List<ArtifactObject> g3bundles = g3.getArtifacts();
        List<LicenseObject> g1licenses = g1.getLicenses();
        List<LicenseObject> g2licenses = g2.getLicenses();
        List<LicenseObject> g3licenses = g3.getLicenses();

        assert g1licenses.size() == 0 : "Group one should not have any associations to licenses; we found " + g1licenses.size() + ".";
        assert g2licenses.size() == 0 : "Group two should not have any associations to licenses; we found " + g2licenses.size() + ".";
        assert g3licenses.size() == 0 : "Group three should not have any associations to licenses; we found " + g3licenses.size() + ".";

        List<GroupObject> b1expectedGroups = new ArrayList<GroupObject>();
        b1expectedGroups.add(g2);
        b1expectedGroups.add(g3);
        List<GroupObject> b2expectedGroups = new ArrayList<GroupObject>();
        b2expectedGroups.add(g1);
        b2expectedGroups.add(g3);

        List<ArtifactObject> g1expectedBundles = new ArrayList<ArtifactObject>();
        g1expectedBundles.add(b2);
        List<ArtifactObject> g2expectedBundles = new ArrayList<ArtifactObject>();
        g2expectedBundles.add(b1);
        List<ArtifactObject> g3expectedBundles = new ArrayList<ArtifactObject>();
        g3expectedBundles.add(b1);
        g3expectedBundles.add(b2);

        assert b1groups.containsAll(b1expectedGroups) && b1expectedGroups.containsAll(b1groups) : "b1 should be associated to exactly groups 2 and 3.";
        assert b2groups.containsAll(b2expectedGroups) && b2expectedGroups.containsAll(b2groups) : "b2 should be associated to exactly groups 1 and 3.";

        assert g1bundles.containsAll(g1expectedBundles) && g1expectedBundles.containsAll(g1bundles) : "g1 should be associated to exactly bundle 2.";
        assert g2bundles.containsAll(g2expectedBundles) && g2expectedBundles.containsAll(g2bundles) : "g2 should be associated to exactly bundle 1.";
        assert g3bundles.containsAll(g3expectedBundles) && g3expectedBundles.containsAll(g3bundles) : "g3 should be associated to exactly bundles 1 and 2.";

        m_artifact2groupRepository.remove(b2g4);

        b1groups = b1.getGroups();
        b2groups = b2.getGroups();
        g1bundles = g1.getArtifacts();
        g2bundles = g2.getArtifacts();
        g3bundles = g3.getArtifacts();

        b2expectedGroups.remove(g3);
        g3expectedBundles.remove(b2);

        assert b1groups.containsAll(b1expectedGroups) && b1expectedGroups.containsAll(b1groups) : "b1 should be associated to exactly groups 2 and 3.";
        assert b2groups.containsAll(b2expectedGroups) && b2expectedGroups.containsAll(b2groups) : "b2 should be associated to exactly group 1.";

        assert g1bundles.containsAll(g1expectedBundles) && g1expectedBundles.containsAll(g1bundles) : "g1 should be associated to exactly bundle 2.";
        assert g2bundles.containsAll(g2expectedBundles) && g2expectedBundles.containsAll(g2bundles) : "g2 should be associated to exactly bundle 1.";
        assert g3bundles.containsAll(g3expectedBundles) && g3expectedBundles.containsAll(g3bundles) : "g3 should be associated to exactly bundle 1.";
    }


    /**
     * Not a full-fledged testcase, but a quick test of the correctness of the
     * specified classes for groups, licenses and their associations. In essence,
     * this test 'touches' all code which uses generic code which has been tested
     * by TestAssociations.
     */
    @Test( groups = { TestUtils.UNIT } )
    public void TestGroup2LicenseAssociations() {
        initializeRepositoryAdmin();
        GroupObject g1 = createBasicGroupObject("group1");
        LicenseObject l1 = createBasicLicenseObject("license1");
        Group2LicenseAssociation g2l1 = m_group2licenseRepository.create(g1, l1);

        assert (g2l1.getLeft().size() == 1) && g2l1.getLeft().contains(g1) : "Left side of the association should be our group.";
        assert (g2l1.getRight().size() == 1) &&  g2l1.getRight().contains(l1) : "Right side of the association should be our license.";

        assert g1.getArtifacts().size() == 0 : "Group 1 should not be associated with any bundles; it is associated with " + g1.getArtifacts().size() + ".";
        assert g1.getLicenses().size() == 1 : "Group 1 should be associated with exactly one license; it is associated with " + g1.getLicenses().size() + ".";

        assert l1.getGroups().size() == 1 : "License 1 should be associated with exactly one group; it is associated with " + l1.getGroups().size() + ".";
        assert l1.getGateways().size() == 0 : "License 1 should not be associated with any gateways; it is associated with " + l1.getGateways().size() + ".";
    }

    /**
     * Not a full-fledged testcase, but a quick test of the correctness of the
     * specified classes for licenses, gateways and their associations. In essence,
     * this test 'touches' all code which uses generic code which has been tested
     * by TestAssociations.
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testLicense2GatewayAssociations() {
        initializeRepositoryAdmin();
        LicenseObject l1 = createBasicLicenseObject("license1");
        GatewayObject g1 = createBasicGatewayObject("gateway1");
        m_license2gatewayRepository.create(l1, g1);

        assert l1.getGroups().size() == 0 : "License 1 should not be associated with any groups; it is associated with " + l1.getGroups().size() + ".";
        assert l1.getGateways().size() == 1 : "License 1 should be associated with exactly one gateway; it is associated with " + l1.getGateways().size() + ".";

        assert g1.getLicenses().size() == 1 : "Gateway 1 should be associated with exactly one license; it is associated with " + g1.getLicenses().size() + ".";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testGetAssociationsWith() {
        initializeRepositoryAdmin();
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        GroupObject g1 = createBasicGroupObject("group1");
        Artifact2GroupAssociation b2g1 = m_artifact2groupRepository.create(b1, g1);

        List<Artifact2GroupAssociation> b1Associations = b1.getAssociationsWith(g1);
        List<Artifact2GroupAssociation> g1Associations = g1.getAssociationsWith(b1);

        assert b1Associations.size() == 1 : "The bundle has exactly one association to the group, but it shows " + b1Associations.size() + ".";
        assert b1Associations.get(0) == b2g1 : "The bundle's association should be the one we created.";

        assert g1Associations.size() == 1 : "The group has exactly one association to the bundle.";
        assert g1Associations.get(0) == b2g1 : "The group's association should be the one we created.";
    }

    /**
     * Tests the correctness of the equals() in RepositoryObject.
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testEquals() {
        List<ArtifactObject> bundles = new ArrayList<ArtifactObject>();
        bundles.add(createBasicBundleObject("bundle1"));
        bundles.add(createBasicBundleObject("bundle2"));
        bundles.get(1).addTag("thetag", "thevalue");
        bundles.add(createBasicBundleObject("bundle3"));

        List<ArtifactObject> backupBundles = new ArrayList<ArtifactObject>();
        backupBundles.addAll(bundles);

        for (ArtifactObject b : backupBundles) {
            bundles.remove(b);
        }

        assert bundles.size() == 0 : "The bundles list should be empty; if not, the ArtifactObject's equals() could be broken.";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testDeploymentVersion() {
        DeploymentVersionObject version = createBasicDeploymentVersionObject("gateway1", "1", new String[] {"bundle1", "bundle2"});

        assert version.getDeploymentArtifacts().length == 2 : "We expect to find two bundles, but we find " + version.getDeploymentArtifacts().length;
        assert version.getDeploymentArtifacts()[0].getUrl().equals("bundle1");
        assert version.getDeploymentArtifacts()[1].getUrl().equals("bundle2");

        ((DeploymentArtifactImpl) version.getDeploymentArtifacts()[0]).addDirective("myDirective", "myValue");

        try {
            createBasicDeploymentVersionObject("gateway1", "1", new String[] {"bundle1", "bundle2"});
            assert false : "Creating a deployment version with a gateway and version that already exists should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        assert m_deploymentVersionRepository.get().size() == 1 : "The disallowed version should not be in the repository; we find " + m_deploymentVersionRepository.get().size();
        assert m_deploymentVersionRepository.get().get(0) == version : "Only our newly created version object should be in the repository.";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RepositorySet deployment = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_deploymentVersionRepository}, null, "", true);
        new RepositorySerializer(deployment).toXML(buffer);
        initializeRepositoryAdmin();

        assert m_deploymentVersionRepository.get().size() == 0;

        deployment = new RepositorySet(null, null, null, null, new ObjectRepositoryImpl[] {m_deploymentVersionRepository}, null, "", true);
        new RepositorySerializer(deployment).fromXML(new ByteArrayInputStream(buffer.toByteArray()));


        assert m_deploymentVersionRepository.get().size() == 1 : "The disallowed version should not be in the repository.";
        assert m_deploymentVersionRepository.get().get(0).equals(version) : "Only our newly created version object should be in the repository.";

        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts().length == 2 : "We expect to find two bundles, but we find " + m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts().length;
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getUrl().equals("bundle1");
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getKeys().length == 1 : "We expect to find one directive in the first artifact.";
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[0].getDirective("myDirective").equals("myValue") : "The directive should be 'myValue'.";
        assert m_deploymentVersionRepository.get().get(0).getDeploymentArtifacts()[1].getUrl().equals("bundle2");
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testDeploymentRepository() {
        DeploymentVersionObject version11 = createBasicDeploymentVersionObject("gateway1", "1", new String[] {"bundle1", "bundle2"});
        DeploymentVersionObject version12 = createBasicDeploymentVersionObject("gateway1", "2", new String[] {"bundle3", "bundle4"});
        // Note the different order in adding the versions for gateway2.
        DeploymentVersionObject version22 = createBasicDeploymentVersionObject("gateway2", "2", new String[] {"bundleC", "bundleD"});
        DeploymentVersionObject version21 = createBasicDeploymentVersionObject("gateway2", "1", new String[] {"bundleA", "bundleB"});

        assert m_deploymentVersionRepository.getDeploymentVersions("NotMyGateway").size() == 0 : "The deployment repository should not return" +
        		"any versions when we ask for a gateway that does not exist, but it returns " + m_deploymentVersionRepository.getDeploymentVersions("NotMyGateway").size();

        List<DeploymentVersionObject> for1 = m_deploymentVersionRepository.getDeploymentVersions("gateway1");
        assert for1.size() == 2 : "We expect two versions for gateway1, but we find " + for1.size();
        assert for1.get(0) == version11 : "The first version for gateway1 should be version11";
        assert for1.get(1) == version12 : "The second version for gateway1 should be version12";

        List<DeploymentVersionObject> for2 = m_deploymentVersionRepository.getDeploymentVersions("gateway2");
        assert for2.size() == 2 : "We expect two versions for gateway2, but we find " + for2.size();
        assert for2.get(0) == version21 : "The first version for gateway2 should be version21";
        assert for2.get(1) == version22 : "The second version for gateway2 should be version22";

        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("NotMyGateway") == null : "The most recent version for a non-existent gateway should not exist.";
        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("gateway1") == version12 : "The most recent version for gateway1 should be version12";
        assert m_deploymentVersionRepository.getMostRecentDeploymentVersion("gateway2") == version22 : "The most recent version for gateway2 should be version22";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testDeploymentRepositoryFilter() {

        String gwId = "\\ ( * ) gateway1)";
        DeploymentVersionObject version1 = createBasicDeploymentVersionObject(gwId, "1", new String[] {"bundle1", "bundle2"});

        List<DeploymentVersionObject> for1 = m_deploymentVersionRepository.getDeploymentVersions( gwId );
        assert for1.size() == 1 : "We expect one version for" + gwId + ", but we find " + for1.size();
        assert for1.get(0) == version1 : "The only version for" + gwId +  "should be version1";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testAssociationsWithLists() {
        ArtifactObject b1 = createBasicBundleObject("b1");
        ArtifactObject b2 = createBasicBundleObject("b2");
        ArtifactObject b3 = createBasicBundleObject("b3");
        GroupObject g1 = createBasicGroupObject("g1");
        GroupObject g2 = createBasicGroupObject("g2");
        GroupObject g3 = createBasicGroupObject("g3");

        List<ArtifactObject> bundles = new ArrayList<ArtifactObject>();
        bundles.add(b1);
        bundles.add(b2);
        List<GroupObject> groups = new ArrayList<GroupObject>();
        groups.add(g1);
        groups.add(g3);

        Artifact2GroupAssociation bg = m_artifact2groupRepository.create(bundles, groups);

        assert bg.getLeft().size() == 2 : "We expect two bundles on the left side of the association.";
        assert bg.getRight().size() == 2 : "We expect two groups on the right side of the association.";

        assert bg.getLeft().contains(b1) : "b1 should be on the left side of the association.";
        assert bg.getLeft().contains(b2) : "b2 should be on the left side of the association.";
        assert !bg.getLeft().contains(b3) : "b3 should not be on the left side of the association.";
        assert bg.getRight().contains(g1) : "g1 should be on the right side of the association.";
        assert !bg.getRight().contains(g2) : "g2 should not be on the right side of the association.";
        assert bg.getRight().contains(g3) : "g3 should be on the right side of the association.";

        List<GroupObject> foundGroups = b1.getGroups();
        assert foundGroups.size() == 2 : "b1 should be associated with two groups.";
        assert foundGroups.contains(g1) : "b1 should be associated with g1";
        assert !foundGroups.contains(g2) : "b1 not should be associated with g2";
        assert foundGroups.contains(g3) : "b1 should be associated with g3";

        foundGroups = b3.getGroups();
        assert foundGroups.size() == 0 : "b3 should not be associated with any groups.";

        List<ArtifactObject> foundBundles = g3.getArtifacts();
        assert foundBundles.size() == 2 : "g1 should be associated with two groups.";
        assert foundBundles.contains(b1) : "g1 should be associated with b1";
        assert foundBundles.contains(b2) : "g1 should be associated with b2";
        assert !foundBundles.contains(b3) : "g1 should not be associated with b3";
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testAssociationsWithCardinality() {
        ArtifactObject b1 = createBasicBundleObject("b1");
        GroupObject g1 = createBasicGroupObject("g1");
        GroupObject g2 = createBasicGroupObject("g2");
        GroupObject g3 = createBasicGroupObject("g3");

        Map<String, String> props = new HashMap<String, String>();
        props.put(Association.LEFT_ENDPOINT, "(" + BundleHelper.KEY_SYMBOLICNAME + "=b1)");
        props.put(Association.LEFT_CARDINALITY, "1");
        props.put(Association.RIGHT_ENDPOINT, "(" + GroupObject.KEY_NAME + "=g*)");
        props.put(Association.RIGHT_CARDINALITY, "2");
        Map<String, String> tags = new HashMap<String, String>();

        try {
            m_artifact2groupRepository.create(props, tags);
            assert false : "There are three matches for the group, but we have a cardinality of 2; we should expect a NPE because no comparator is provided.";
        }
        catch (NullPointerException npe) {
            //expected
        }

        props.put(Association.RIGHT_CARDINALITY, "3");

        Artifact2GroupAssociation bg = m_artifact2groupRepository.create(props, tags);
        assert b1.getGroups().size() == 3 : "The bundle should be associated to three groups.";
        assert (g1.getArtifacts().size() == 1) && g1.getArtifacts().contains(b1) : "g1 should be associated to only b1.";
        assert (g2.getArtifacts().size() == 1) && g2.getArtifacts().contains(b1) : "g1 should be associated to only b1.";
        assert (g3.getArtifacts().size() == 1) && g3.getArtifacts().contains(b1) : "g1 should be associated to only b1.";
    }

    private Filter createLocalFilter(String filter) throws InvalidSyntaxException {
        return new org.apache.felix.framework.FilterImpl(filter);
    }

    private ArtifactObject createBasicBundleObject(String symbolicName) {
        return createBasicBundleObject(symbolicName, null);
    }

    private ArtifactObject createBasicBundleObject(String symbolicName, String version) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(BundleHelper.KEY_SYMBOLICNAME, symbolicName);
        attr.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        attr.put(ArtifactObject.KEY_URL, "http://" + symbolicName + "-v" + ((version == null) ? "null" : version));
        Map<String, String> tags = new HashMap<String, String>();

        if (version != null) {
            attr.put(BundleHelper.KEY_VERSION, version);
        }
        return m_artifactRepository.create(attr, tags);
    }

    private GroupObject createBasicGroupObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(GroupObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_groupRepository.create(attr, tags);
    }

    private LicenseObject createBasicLicenseObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(LicenseObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_licenseRepository.create(attr, tags);
    }

    private GatewayObject createBasicGatewayObject(String id) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, id);
        Map<String, String> tags = new HashMap<String, String>();

        return m_gatewayRepository.create(attr, tags);
    }

    private DeploymentVersionObject createBasicDeploymentVersionObject(String gatewayID, String version, String[] bundles) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DeploymentVersionObject.KEY_GATEWAYID, gatewayID);
        attr.put(DeploymentVersionObject.KEY_VERSION, version);
        Map<String, String> tags = new HashMap<String, String>();

        List<DeploymentArtifactImpl> artifacts = new ArrayList<DeploymentArtifactImpl>();
        for (String s : bundles) {
            artifacts.add(new DeploymentArtifactImpl(s));
        }
        return m_deploymentVersionRepository.create(attr, tags, artifacts.toArray(new DeploymentArtifact[0]));
    }
}
