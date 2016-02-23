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
package org.apache.ace.it.repositoryadmin;

import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ADDED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_REMOVED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_STATUS_CHANGED;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.Event;
import org.osgi.service.useradmin.User;

public class RepositoryAdminTest extends BaseRepositoryAdminTest {

    public void testAssociationsWithMovingEndpoints() throws Exception {
        final ArtifactObject b1 = createBasicBundleObject("thebundle", "1", null);
        final FeatureObject g1 = createBasicFeatureObject("thefeature");

        final Artifact2FeatureAssociation bg = runAndWaitForEvent(new Callable<Artifact2FeatureAssociation>() {
            public Artifact2FeatureAssociation call() throws Exception {
                Map<String, String> properties = new HashMap<>();
                properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "[1,3)");
                return m_artifact2featureRepository.create(b1, properties, g1, null);
            }
        }, false, Artifact2FeatureAssociation.TOPIC_ADDED);

        assertTrue("The left side of the association should now be b1; we find "
            + bg.getLeft().size() + " bundles on the left side of the association.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b1));
        assertTrue("The right side of the association should now be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertEquals("b1 should be assocated with g1", g1, b1.getFeatures().get(0));
        assertEquals("g1 should be assocated with b1", b1, g1.getArtifacts().get(0));

        final ArtifactObject b2 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("thebundle", "2", null);
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED);

        assertTrue("The left side of the association should no longer be b1; we find "
            + bg.getLeft().size() + " bundles.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1));
        assertTrue("The left side of the association should now be b2.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b2));
        assertTrue("The right side of the association should now be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertEquals("b1 should not be associated with any feature.", 0, b1.getFeatures().size());
        assertEquals("b2 should now be assocation with g1", g1, b2.getFeatures().get(0));
        assertEquals("g1 should be assocation with b2", b2, g1.getArtifacts().get(0));
        assertEquals("g1 should be associated with one bundle", 1, g1.getArtifacts().size());

        ArtifactObject b3 = createBasicBundleObject("thebundle", "3", null);

        assertTrue("The left side of the association should no longer be b1.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1));
        assertTrue("The left side of the association should now be b2.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b2));
        assertTrue("The left side of the association should not be b3.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3));
        assertTrue("The right side of the association should now be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertEquals("b1 should not be associated with any feature.", 0, b1.getFeatures().size());
        assertEquals("b2 should now be assocation with g1", g1, b2.getFeatures().get(0));
        assertEquals("b3 should not be associated with any feature.", 0, b3.getFeatures().size());
        assertEquals("g1 should be assocation with b2", b2, g1.getArtifacts().get(0));
        assertEquals("g1 should be associated with one bundle", 1, g1.getArtifacts().size());

        ArtifactObject b15 = createBasicBundleObject("thebundle", "1.5", null);

        assertTrue("The left side of the association should no longer be b1.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1));
        assertTrue("The left side of the association should not be b15.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b15));
        assertTrue("The left side of the association should now be b2.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b2));
        assertTrue("The left side of the association should not be b3.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3));
        assertTrue("The right side of the association should now be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertEquals("b1 should not be associated with any feature.", 0, b1.getFeatures().size());
        assertEquals("b15 should not be associated with any feature.", 0, b15.getFeatures().size());
        assertEquals("b2 should now be assocation with g1", g1, b2.getFeatures().get(0));
        assertEquals("b3 should not be associated with any feature.", 0, b3.getFeatures().size());
        assertEquals("g1 should be assocation with b2", b2, g1.getArtifacts().get(0));
        assertEquals("g1 should be associated with one bundle", 1, g1.getArtifacts().size());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_artifactRepository.remove(b2);
                return null;
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED);

        // note that we cannot test anything for b2: this has been removed, and now has no
        // defined state.
        assertTrue("The left side of the association should no longer be b1.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1));
        assertTrue("The left side of the association should now be b15.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b15));
        assertTrue("The left side of the association should not be b3.", (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3));
        assertTrue("The right side of the association should now be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertEquals("b1 should not be associated with any feature.", 0, b1.getFeatures().size());
        assertEquals("b15 should now be assocation with g1", g1, b15.getFeatures().get(0));
        assertEquals("b3 should not be associated with any feature.", 0, b3.getFeatures().size());
        assertEquals("g1 should be assocation with b15", b15, g1.getArtifacts().get(0));
        assertEquals("g1 should be associated with one bundle", 1, g1.getArtifacts().size());

        cleanUp();
    }

    public void testAutoApprove() throws Exception {
        User user = new MockUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext
            .add(loginContext.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target").setWriteable())
            .add(loginContext.createDeploymentRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("deployment").setWriteable());

        m_repositoryAdmin.login(loginContext);

        m_repositoryAdmin.checkout();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicTargetObject("testAutoApproveTarget");
                return null;
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        final StatefulTargetObject sgo = m_statefulTargetRepository.get(
            m_bundleContext.createFilter("(" + TargetObject.KEY_ID + "=" + "testAutoApproveTarget)")).get(0);

        // Set up some deployment information for the target.
        final FeatureObject g = runAndWaitForEvent(new Callable<FeatureObject>() {
            public FeatureObject call() throws Exception {
                ArtifactObject b = createBasicBundleObject("myBundle", "1.0", null);
                FeatureObject g = createBasicFeatureObject("myFeature");
                DistributionObject l = createBasicDistributionObject("myDistribution");
                m_artifact2featureRepository.create(b, g);
                m_feature2distributionRepository.create(g, l);
                m_distribution2targetRepository.create(l, sgo.getTargetObject());
                return g;
            }
        }, false, ArtifactObject.TOPIC_ADDED, FeatureObject.TOPIC_ADDED, DistributionObject.TOPIC_ADDED,
            Artifact2FeatureAssociation.TOPIC_ADDED, Feature2DistributionAssociation.TOPIC_ADDED,
            Distribution2TargetAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertTrue("We added some deployment information, so the target should need approval.", sgo.needsApprove());

        sgo.setAutoApprove(true);

        assertTrue("Turning on the autoapprove should not automatically approve whatever was waiting.", sgo.needsApprove());

        sgo.approve();

        List<Event> events = new ArrayList<>();
        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, events, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED, RepositoryAdmin.TOPIC_REFRESH);

        assertFalse("We approved the new version by hand, so we should not need approval.", sgo.needsApprove());
        assertContainsRefreshCause(events, "commit");

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                ArtifactObject b = createBasicBundleObject("myBundle2", "1.0", null);
                m_artifact2featureRepository.create(b, g);
                return null;
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2FeatureAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED,
            TOPIC_STATUS_CHANGED);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertFalse("With autoapprove on, adding new deployment information should still not need approval (at least, after the two CHANGED events).", sgo.needsApprove());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(sgo.getID());
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED, TOPIC_REMOVED);
    }

    public void testImportArtifactGeneralBundle() throws Exception {
        // Use a valid JAR file, without a Bundle-SymbolicName header.
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1");
        attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");

        File temp = File.createTempFile("org.apache.ace.test1", ".jar");
        temp.deleteOnExit();
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), true);
            fail("Without a Bundle-SymbolicName header, the BundleHelper cannot recognize this bundle.");
        }
        catch (IllegalArgumentException re) {
            // expected
        }

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), "notTheBundleMime", true);
            fail("We have given an illegal mimetype, so no recognizer or helper can be found.");
        }
        catch (IllegalArgumentException re) {
            // expected
        }

        // Use a valid JAR file, with a Bundle-SymbolicName header, but do not supply an OBR.
        attributes.putValue(BundleHelper.KEY_SYMBOLICNAME, String.format("org.apache.ace.test-%d; singleton:=true", System.currentTimeMillis()));

        temp = File.createTempFile("org.apache.ace.test2", ".jar");
        temp.deleteOnExit();
        jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), true);
            fail("No OBR has been started, so the artifact repository should complain that there is no storage available.");
        }
        catch (IOException ise) {
            // expected
        }

        // Supply the OBR.
        addObr("/obr", "store");

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assertTrue(m_artifactRepository.get().size() == 1);
        assertTrue(m_artifactRepository.getResourceProcessors().size() == 0);

        // Create a JAR file which looks like a resource processor supplying bundle.
        attributes.putValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "someProcessor");
        attributes.putValue(BundleHelper.KEY_VERSION, "1.0.0.processor");

        temp = File.createTempFile("org.apache.ace.test3", ".jar");
        temp.deleteOnExit();
        jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assertTrue(m_artifactRepository.get().size() == 1);
        assertTrue(m_artifactRepository.getResourceProcessors().size() == 1);
    }

    public void testImportArtifactInvalidURL() throws Exception {
        try {
            m_artifactRepository.importArtifact(null, true);
            fail("A null URL cannot be imported into an artifact repository.");
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        URL invalidfile = new URL("file:/thisfilecannotexist");

        try {
            m_artifactRepository.importArtifact(invalidfile, true);
            fail("An illegal URL should result in an error.");
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        cleanUp();
    }

    public void testLoginLogoutAndLoginOnceAgainWhileCreatingAnAssociation() throws IOException, InterruptedException,
        InvalidSyntaxException {
        User user1 = new MockUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target").setWriteable());

        m_repositoryAdmin.login(loginContext1);

        FeatureObject g1 = createBasicFeatureObject("feature1");
        DistributionObject l1 = createBasicDistributionObject("distribution1");

        m_feature2distributionRepository.create(g1, l1);

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);
    }

    /**
     * Tests read only repository access: marking a repository as readonly for a login should mean that it does not get
     * committed, but local changes will stay around between logins.
     */
    public void testReadOnlyRepositoryAccess() throws Exception {
        User user1 = new MockUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target"));

        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        createBasicFeatureObject("feature1");
        createBasicTargetObject("target1");

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        assertEquals("We expect our own feature object in the repository;", 1, m_featureRepository.get().size());
        assertEquals("We expect our own target object in the repository;", 1, m_targetRepository.get().size());

        m_repositoryAdmin.commit();

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        assertEquals("We expect our own feature object in the repository;", 1, m_featureRepository.get().size());
        assertEquals("Since the target repository will not be committed, we expect no target objects in the repository;", 0, m_targetRepository.get().size());
    }

    /**
     * Add a bundle, feature and distribution, associate all, remove the feature, No associations should be left.
     * 
     * @throws Exception
     */
    public void testRemoveBundleFeature() throws Exception {
        final ArtifactObject b1 = createBasicBundleObject("thebundle", "1", null);
        final FeatureObject g1 = createBasicFeatureObject("thefeature");

        final Artifact2FeatureAssociation bg = runAndWaitForEvent(new Callable<Artifact2FeatureAssociation>() {
            public Artifact2FeatureAssociation call() throws Exception {
                return m_artifact2featureRepository.create("(&(" + BundleHelper.KEY_SYMBOLICNAME + "=thebundle)(|("
                    + BundleHelper.KEY_VERSION + ">=1)(" + BundleHelper.KEY_VERSION + "=<3))(!("
                    + BundleHelper.KEY_VERSION + "=3)))", "(name=thefeature)");
            }
        }, false, Artifact2FeatureAssociation.TOPIC_ADDED);

        final DistributionObject l1 = createBasicDistributionObject("thedistribution");

        final Feature2DistributionAssociation gtl = runAndWaitForEvent(new Callable<Feature2DistributionAssociation>() {
            public Feature2DistributionAssociation call() throws Exception {
                return m_feature2distributionRepository.create("(name=thefeature)", "(name=thedistribution)");
            }
        }, false, Feature2DistributionAssociation.TOPIC_ADDED);

        assertTrue("The left side of the BG-association should be b1.", (bg.getLeft().size() == 1) && bg.getLeft().contains(b1));
        assertTrue("The right side of the BG-association should be g1.", (bg.getRight().size() == 1) && bg.getRight().contains(g1));
        assertTrue("The left side of the GtL-association should be g1.", (gtl.getLeft().size() == 1) && gtl.getLeft().contains(g1));
        assertTrue("The right side of the GtL-association should be l1.", (gtl.getRight().size() == 1) && gtl.getRight().contains(l1));
        assertTrue("The bundlefeature association should be satisfied.", bg.isSatisfied());
        assertTrue("The feature2distribution association should be satisfied.", gtl.isSatisfied());
        assertEquals("Bundle b1 should be associated to one feature.", 1, b1.getFeatures().size());
        assertEquals("Distribution l1 should be associated to one feature.", 1, l1.getFeatures().size());

        // remove the feature
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_featureRepository.remove(g1);
                return null;
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED, Feature2DistributionAssociation.TOPIC_CHANGED);

        assertFalse("The bundlefeature association shouldn not be satisfied.", gtl.isSatisfied());
        assertFalse("The feature2distribution assocation should not be satisfied.", bg.isSatisfied());

        assertEquals("Bundle b1 shouldn't be associated to any feature, but is associated to " + b1.getFeatures(), 0, b1.getFeatures().size());
        assertEquals("Distribution l1 shouldn't be associated to any feature.", 0, l1.getFeatures().size());

        cleanUp();
    }

    /**
     * Tests the behavior with logging in and out (with multiple users), and communication with the server.
     * 
     * @throws Exception
     */
    public void testRepositoryAdmin() throws Exception {
        final User user1 = new MockUser();
        final User user2 = new MockUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        try {
            m_repositoryAdmin.checkout();
            fail("Without being logged in, it should not be possible to do checkout.");
        }
        catch (IllegalStateException ise) {
            // expected
        }

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target").setWriteable());

        m_repositoryAdmin.login(loginContext1);

        assertFalse("When first logging in without checking out, the repository cannot be current.", m_repositoryAdmin.isCurrent());
        assertFalse("Immediately after login, the repository not is modified.", m_repositoryAdmin.isModified());

        try {
            m_repositoryAdmin.commit();
            fail("We should not be able to commit before we check something out.");
        }
        catch (IllegalStateException e) {
            // expected
        }

        List<Event> events = new ArrayList<>();
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.checkout();
                return null;
            }
        }, false, events, RepositoryAdmin.TOPIC_REFRESH);

        assertTrue("After initial checkout, the repository is current.", m_repositoryAdmin.isCurrent());
        assertFalse("Immediately after login, the repository cannot be modified.", m_repositoryAdmin.isModified());
        assertContainsRefreshCause(events, "checkout");

        ArtifactObject b1 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle1");
            }
        }, false, ArtifactObject.TOPIC_ADDED, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assertTrue("After initial checkout, the repository is current.", m_repositoryAdmin.isCurrent());
        assertTrue("We have added a bundle, so the repository is modified.", m_repositoryAdmin.isModified());
        assertEquals(1, m_artifactRepository.get().size());
        assertEquals("We expect the working state of our bundle to be New;", WorkingState.New, m_repositoryAdmin.getWorkingState(b1));
        assertEquals("We expect one bundle object in working state New;", 1, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New));
        assertEquals("We expect 0 bundle objects in working state Changed;", 0, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed));
        assertEquals("We expect 0 bundle objects in working state Unchanged;", 0, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged));

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.logout(false);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        cleanUp();

        assertEquals(0, m_artifactRepository.get().size());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext1);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assertTrue("There has not been another commit in between, so we are still current.", m_repositoryAdmin.isCurrent());
        assertTrue("We have made changes since the last commit, so the repository must be modified.", m_repositoryAdmin.isModified());
        assertEquals(1, m_artifactRepository.get().size());
        assertEquals("We expect the working state of our bundle to be New;", WorkingState.New, m_repositoryAdmin.getWorkingState(b1));
        assertEquals("We expect one bundle object in working state New;", 1, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New));
        assertEquals("We expect 0 bundle objects in working state Changed;", 0, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed));
        assertEquals("We expect 0 bundle objects in working state Unchanged;", 0, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged));

        m_repositoryAdmin.commit();

        assertTrue("After a commit, the repository must be current.", m_repositoryAdmin.isCurrent());
        assertFalse("After a commit, the repository cannot be modified.", m_repositoryAdmin.isModified());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.logout(false);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        cleanUp();

        final RepositoryAdminLoginContext loginContext2 = m_repositoryAdmin.createLoginContext(user2);
        loginContext2
            .add(loginContext2.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext2.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target").setWriteable());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext2);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assertEquals(0, m_artifactRepository.get().size());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.checkout();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_REFRESH);

        assertEquals("We expect to find 1 bundle after checkout;", 1, m_artifactRepository.get().size());
        assertTrue("After a checkout, without changing anything, the repository must be current.", m_repositoryAdmin.isCurrent());
        assertFalse("After a checkout, without changing anything, the repository cannot be modified.", m_repositoryAdmin.isModified());

        ArtifactObject b2 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle2");
            }
        }, false, ArtifactObject.TOPIC_ADDED, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assertTrue(m_artifactRepository.get().size() == 2);
        assertTrue("After changing something in memory without flushing it, the repository still is current.", m_repositoryAdmin.isCurrent());
        assertTrue("We have added something, so the repository is modified.", m_repositoryAdmin.isModified());
        assertEquals("We expect the working state of our bundle1 to be Unchanged;", WorkingState.Unchanged, m_repositoryAdmin.getWorkingState(b1));
        assertEquals("We expect the working state of our bundle2 to be New;", WorkingState.New, m_repositoryAdmin.getWorkingState(b2));
        assertEquals("We expect one bundle object in working state New;", 1, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New));
        assertEquals("We expect 0 bundle objects in working state Changed;", 0, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed));
        assertEquals("We expect one bundle object in working state Unchanged;", 1, m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged));

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.logout(false);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext1);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assertEquals("We expect 1 item in the bundle repository;", 1, m_artifactRepository.get().size());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.logout(false);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext2);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assertEquals("We expect 2 items in the bundle repository;", 2, m_artifactRepository.get().size());

        events.clear();
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.revert();
                return null;
            }
        }, false, events, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_REFRESH);

        assertEquals("We expect 1 item in the bundle repository;", 1, m_artifactRepository.get().size());
        assertContainsRefreshCause(events, "revert");
    }

    private void assertContainsRefreshCause(List<Event> events, String cause) {
        boolean found = false;
        for (Event event : events) {
            if (RepositoryAdmin.TOPIC_REFRESH.equals(event.getTopic())) {
                assertEquals("Refresh property does not have the correct cause property?!", event.getProperty("cause"), cause);
                found = true;
            }
        }
        assertTrue("Events did not contain expected refresh event!", found);
    }
}
