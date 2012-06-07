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

import static org.apache.ace.client.repository.RepositoryObject.PRIVATE_TOPIC_ROOT;
import static org.apache.ace.client.repository.RepositoryObject.PUBLIC_TOPIC_ROOT;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.KEY_ID;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.KEY_REGISTRATION_STATE;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ADDED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ALL;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_REMOVED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_STATUS_CHANGED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.UNKNOWN_VERSION;
import static org.apache.ace.it.Options.jetty;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import junit.framework.Assert;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
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
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.ProvisioningState;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.RegistrationState;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.StoreState;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.it.Options.Ace;
import org.apache.ace.it.Options.Felix;
import org.apache.ace.it.Options.Knopflerfish;
import org.apache.ace.it.Options.Osgi;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogEvent;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.extra.CleanCachesOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.useradmin.User;
import org.osgi.util.tracker.ServiceTracker;

@RunWith(JUnit4TestRunner.class)
public class RepositoryAdminTest extends IntegrationTestBase implements EventHandler {

    @org.ops4j.pax.exam.junit.Configuration
    public Option[] configuration() {
        return options(
            systemProperty("org.osgi.service.http.port").value("" + TestConstants.PORT),
            new VMOption("-ea"),
            new CleanCachesOption(),
            junitBundles(),
            provision(
                Osgi.compendium(),
                Felix.dependencyManager(),
                jetty(),
                Felix.configAdmin(),
                Felix.preferences(),
                Felix.eventAdmin(),
                Knopflerfish.useradmin(),
                Knopflerfish.log(),
                Ace.util(),
                Ace.authenticationApi(),
                Ace.connectionFactory(),
                Ace.rangeApi(),
                Ace.log(),
                Ace.serverLogStore(),
                Ace.httplistener(),
                Ace.repositoryApi(),
                Ace.repositoryImpl(),
                Ace.repositoryServlet(),
                Ace.configuratorServeruseradmin(),
                Ace.obrMetadata(),
                Ace.obrServlet(),
                Ace.obrStorage(),
                Ace.clientRepositoryApi(),
                Ace.clientRepositoryImpl(),
                Ace.clientRepositoryHelperBase(),
                Ace.clientRepositoryHelperBundle(),
                Ace.clientRepositoryHelperConfiguration(),
                Ace.clientAutomation()
            ));
    }

    protected void before() throws IOException {
        getService(SessionFactory.class).createSession("test-session-ID");
        configureFactory("org.apache.ace.server.log.store.factory",
            "name", "auditlog", "authentication.enabled", "false");
    }

    protected Component[] getDependencies() {
        Dictionary<String, Object> topics = new Hashtable<String, Object>();
        topics.put(EventConstants.EVENT_TOPIC, new String[] { PUBLIC_TOPIC_ROOT + "*",
            PRIVATE_TOPIC_ROOT + "*",
            RepositoryAdmin.PUBLIC_TOPIC_ROOT + "*",
            RepositoryAdmin.PRIVATE_TOPIC_ROOT + "*",
            TOPIC_ALL });
        return new Component[] {
            createComponent()
                .setInterface(EventHandler.class.getName(), topics)
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(RepositoryAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Artifact2FeatureAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(FeatureRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Feature2DistributionAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(DistributionRepository.class).setRequired(true))
                .add(createServiceDependency().setService(Distribution2TargetAssociationRepository.class).setRequired(true))
                .add(createServiceDependency().setService(TargetRepository.class).setRequired(true))
                .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
                .add(createServiceDependency().setService(StatefulTargetRepository.class).setRequired(true))
                .add(createServiceDependency().setService(LogStore.class, "(&(" + Constants.OBJECTCLASS + "=" + LogStore.class.getName() + ")(name=auditlog))").setRequired(true))
                .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
        };
    }

    private volatile ConfigurationAdmin m_configAdmin; /* Injected by dependency manager */
    private volatile RepositoryAdmin m_repositoryAdmin; /* Injected by dependency manager */
    private volatile ArtifactRepository m_artifactRepository; /* Injected by dependency manager */
    private volatile Artifact2FeatureAssociationRepository m_artifact2featureRepository; /* Injected by dependency manager */
    private volatile FeatureRepository m_featureRepository; /* Injected by dependency manager */
    private volatile Feature2DistributionAssociationRepository m_feature2distributionRepository; /* Injected by dependency manager */
    private volatile DistributionRepository m_distributionRepository; /* Injected by dependency manager */
    private volatile Distribution2TargetAssociationRepository m_distribution2targetRepository; /* Injected by dependency manager */
    private volatile TargetRepository m_targetRepository; /* Injected by dependency manager */
    private volatile DeploymentVersionRepository m_deploymentVersionRepository; /* Injected by dependency manager */
    private volatile StatefulTargetRepository m_statefulTargetRepository; /* Injected by dependency manager */
    private volatile LogStore m_auditLogStore; /* Injected by dependency manager */

    public void cleanUp() throws IOException, InvalidSyntaxException, InterruptedException {
        // Simply remove all objects in the repository.
        clearRepository(m_artifactRepository);
        clearRepository(m_artifact2featureRepository);
        clearRepository(m_feature2distributionRepository);
        clearRepository(m_distribution2targetRepository);
        clearRepository(m_artifactRepository);
        clearRepository(m_featureRepository);
        clearRepository(m_distributionRepository);
        clearRepository(m_targetRepository);
        clearRepository(m_deploymentVersionRepository);
        m_statefulTargetRepository.refresh();
        try {
            m_repositoryAdmin.logout(true);
        }
        catch (Exception ioe) {
            // ioe.printStackTrace(System.out);
        }
    }

    public <T extends RepositoryObject> void clearRepository(ObjectRepository<T> rep) {
        for (T entity : rep.get()) {
            rep.remove(entity);
        }
        assert rep.get().size() == 0 : "Something went wrong clearing the repository.";
    }

    /**
     * Add a bundle, feature and distribution, associate all, remove the feature, No associations should be left.
     * 
     * @throws Exception
     */
    @Test
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

        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b1) : "The left side of the BG-association should be b1.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the BG-association should be g1.";
        assert (gtl.getLeft().size() == 1) && gtl.getLeft().contains(g1) : "The left side of the GtL-association should be g1.";
        assert (gtl.getRight().size() == 1) && gtl.getRight().contains(l1) : "The right side of the GtL-association should be l1.";
        assert bg.isSatisfied() : "The bundlefeature association should be satisfied.";
        assert gtl.isSatisfied() : "The feature2distribution association should be satisfied.";
        assert b1.getFeatures().size() == 1 : "Bundle b1 should be associated to one feature.";
        assert l1.getFeatures().size() == 1 : "Distribution l1 should be associated to one feature.";

        // remove the feature
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_featureRepository.remove(g1);
                return null;
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED, Feature2DistributionAssociation.TOPIC_CHANGED);

        assert !gtl.isSatisfied() : "The bundlefeature association shouldn not be satisfied.";
        assert !bg.isSatisfied() : "The feature2distribution assocation should not be satisfied.";

        assert b1.getFeatures().size() == 0 : "Bundle b1 shouldn't be associated to any feature, but is associated to "
            + b1.getFeatures();
        assert l1.getFeatures().size() == 0 : "Distribution l1 shouldn't be associated to any feature.";

        cleanUp();
    }

    @Test
    public void testAssociationsWithMovingEndpoints() throws Exception {
        final ArtifactObject b1 = createBasicBundleObject("thebundle", "1", null);
        final FeatureObject g1 = createBasicFeatureObject("thefeature");
        final Artifact2FeatureAssociation bg = runAndWaitForEvent(new Callable<Artifact2FeatureAssociation>() {
            public Artifact2FeatureAssociation call() throws Exception {
                Map<String, String> properties = new HashMap<String, String>();
                properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "[1,3)");
                return m_artifact2featureRepository.create(b1, properties, g1, null);
            }
        }, false, Artifact2FeatureAssociation.TOPIC_ADDED);

        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b1) : "The left side of the association should now be b1; we find "
            + bg.getLeft().size() + " bundles on the left side of the association.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getFeatures().get(0) == g1 : "b1 should be assocated with g1";
        assert g1.getArtifacts().get(0) == b1 : "g1 should be assocated with b1";

        final ArtifactObject b2 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("thebundle", "2", null);
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1; we find "
            + bg.getLeft().size() + " bundles.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getFeatures().size() == 0 : "b1 should not be associated with any feature.";
        assert b2.getFeatures().get(0) == g1 : "b2 should now be assocation with g1";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        ArtifactObject b3 = createBasicBundleObject("thebundle", "3", null);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getFeatures().size() == 0 : "b1 should not be associated with any feature.";
        assert b2.getFeatures().get(0) == g1 : "b2 should now be assocation with g1";
        assert b3.getFeatures().size() == 0 : "b3 should not be associated with any feature.";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        ArtifactObject b15 = createBasicBundleObject("thebundle", "1.5", null);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b15) : "The left side of the association should not be b15.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getFeatures().size() == 0 : "b1 should not be associated with any feature.";
        assert b15.getFeatures().size() == 0 : "b15 should not be associated with any feature.";
        assert b2.getFeatures().get(0) == g1 : "b2 should now be assocation with g1";
        assert b3.getFeatures().size() == 0 : "b3 should not be associated with any feature.";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_artifactRepository.remove(b2);
                return null;
            }
        }, false, Artifact2FeatureAssociation.TOPIC_CHANGED);

        // note that we cannot test anything for b2: this has been removed, and now has no
        // defined state.
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b15) : "The left side of the association should now be b15.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getFeatures().size() == 0 : "b1 should not be associated with any feature.";
        assert b15.getFeatures().get(0) == g1 : "b15 should now be assocation with g1";
        assert b3.getFeatures().size() == 0 : "b3 should not be associated with any feature.";
        assert g1.getArtifacts().get(0) == b15 : "g1 should be assocation with b15";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        cleanUp();
    }

    private static final String ENDPOINT = "/AdminRepTest";
    private static final String HOST = "http://localhost:" + TestConstants.PORT;

    /**
     * Tests the behavior with logging in and out (with multiple users), and communication
     * with the server.
     * 
     * @throws Exception
     */
    @Test
    public void testRepositoryAdmin() throws Exception {
        final User user1 = new MockUser("user1");
        final User user2 = new MockUser("user2");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        try {
            m_repositoryAdmin.checkout();
            assert false : "Without being logged in, it should not be possible to do checkout.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target").setWriteable());
        m_repositoryAdmin.login(loginContext1);

        assert !m_repositoryAdmin.isCurrent() : "When first logging in without checking out, the repository cannot be current.";
        assert !m_repositoryAdmin.isModified() : "Immediately after login, the repository not is modified.";

        try {
            m_repositoryAdmin.commit();
            assert false : "We should not be able to commit before we check something out.";
        }
        catch (IllegalStateException e) {
            // expected
        }

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.checkout();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_REFRESH);

        assert m_repositoryAdmin.isCurrent() : "After initial checkout, the repository is current.";
        assert !m_repositoryAdmin.isModified() : "Immediately after login, the repository cannot be modified.";

        ArtifactObject b1 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle1");
            }
        }, false, ArtifactObject.TOPIC_ADDED, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assert m_repositoryAdmin.isCurrent() : "After initial checkout, the repository is current.";
        assert m_repositoryAdmin.isModified() : "We have added a bundle, so the repository is modified.";
        assert m_artifactRepository.get().size() == 1;
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.New) : "We expect the working state of our bundle to be New, but it is "
            + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 0 : "We expect 0 bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.logout(false);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        cleanUp();

        assert m_artifactRepository.get().size() == 0;

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext1);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assert m_repositoryAdmin.isCurrent() : "There has not been another commit in between, so we are still current.";
        assert m_repositoryAdmin.isModified() : "We have made changes since the last commit, so the repository must be modified.";
        assert m_artifactRepository.get().size() == 1;
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.New) : "We expect the working state of our bundle to be New, but it is "
            + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 0 : "We expect 0 bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

        m_repositoryAdmin.commit();

        assert m_repositoryAdmin.isCurrent() : "After a commit, the repository must be current.";
        assert !m_repositoryAdmin.isModified() : "After a commit, the repository cannot be modified.";

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
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext2.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target").setWriteable());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.login(loginContext2);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        assert m_artifactRepository.get().size() == 0;

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.checkout();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_REFRESH);

        assert m_artifactRepository.get().size() == 1 : "We expect to find 1 bundle after checkout, but we find "
            + m_artifactRepository.get().size();
        assert m_repositoryAdmin.isCurrent() : "After a checkout, without changing anything, the repository must be current.";
        assert !m_repositoryAdmin.isModified() : "After a checkout, without changing anything, the repository cannot be modified.";

        ArtifactObject b2 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle2");
            }
        }, false, ArtifactObject.TOPIC_ADDED, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assert m_artifactRepository.get().size() == 2;
        assert m_repositoryAdmin.isCurrent() : "After changing something in memory without flushing it, the repository still is current.";
        assert m_repositoryAdmin.isModified() : "We have added something, so the repository is modified.";
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.Unchanged) : "We expect the working state of our bundle1 to be Unchanged, but it is "
            + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getWorkingState(b2).equals(WorkingState.New) : "We expect the working state of our bundle2 to be New, but it is "
            + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 1 : "We expect 1 bundle object in working state New, but we find "
            + m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

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

        assert m_artifactRepository.get().size() == 1 : "We expect 1 item in the bundle repository, in stead of "
            + m_artifactRepository.get().size();

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

        assert m_artifactRepository.get().size() == 2 : "We expect 2 items in the bundle repository, in stead of "
            + m_artifactRepository.get().size();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.revert();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_REFRESH, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assert m_artifactRepository.get().size() == 1 : "We expect 1 item in the bundle repository, in stead of "
            + m_artifactRepository.get().size();

        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
            e.printStackTrace(System.err);
        }

        cleanUp();
    }

    @Test
    public void testAutoApprove() throws Exception {
        User user = new MockUser("user");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext
            .add(loginContext.createShopRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target").setWriteable())
            .add(loginContext.createDeploymentRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("deployment").setWriteable());

        m_repositoryAdmin.login(loginContext);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicTargetObject("testAutoApproveTarget");
                return null;
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        final StatefulTargetObject sgo =
            m_statefulTargetRepository.get(
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

        assert sgo.needsApprove() : "We added some deployment information, so the target should need approval.";

        sgo.setAutoApprove(true);

        assert sgo.needsApprove() : "Turning on the autoapprove should not automatically approve whatever was waiting.";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo.approve();
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "We approved the new version by hand, so we should not need approval.";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                ArtifactObject b = createBasicBundleObject("myBundle2", "1.0", null);
                m_artifact2featureRepository.create(b, g);
                return null;
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2FeatureAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED,
            TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "With autoapprove on, adding new deployment information should still not need approval (at least, after the two CHANGED events).";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(sgo.getID());
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED, TOPIC_REMOVED);

        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
            e.printStackTrace(System.err);
        }
        cleanUp();
    }

    @Test
    public void testStateful() throws Exception {
        User user = new MockUser("user");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext
            .add(loginContext.createShopRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target").setWriteable())
            .add(loginContext.createDeploymentRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("deployment").setWriteable());

        m_repositoryAdmin.login(loginContext);

        /*
         * First, test some functionality without auditlog data.
         * I would prefer to test without an auditlog present at all, but that
         * is not so easily done.
         */
        testStatefulCreateRemove();
        testStatefulSetAutoApprove();
        testStatefulApprove();

        testStatefulAuditlog();
        testStatefulAuditAndRegister();
        testStatefulAuditAndRemove();
        testStrangeNamesInTargets();

        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
            e.printStackTrace(System.err);
        }

        cleanUp();
    }

    private void testStatefulSetAutoApprove() throws Exception {

        // register target with
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "a_target");
        attr.put(TargetObject.KEY_AUTO_APPROVE, String.valueOf(true));
        final Map<String, String> tags = new HashMap<String, String>();

        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assert m_targetRepository.get().size() == 1 : "We expect to find exactly one target in the repository, but we find "
            + m_targetRepository.get().size();
        assert m_statefulTargetRepository.get().size() == 1 : "We expect to find exactly one stateful target in the repository, but we find "
            + m_statefulTargetRepository.get().size();

        assert sgo.getAutoApprove() : "The target should have auto approved value: true but got: false.";

        sgo.setAutoApprove(false);

        assert !sgo.getAutoApprove() : "The target should have auto approved value: false but got: true.";

        // clean up
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(sgo.getID());
                return null;
            }
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);
    }

    private void testStatefulCreateRemove() throws Exception {
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "myNewTarget1");
        final Map<String, String> tags = new HashMap<String, String>();

        try {
            m_statefulTargetRepository.create(attr, tags);
            assert false : "Creating a stateful target repository should not be allowed.";
        }
        catch (UnsupportedOperationException uoe) {
            // expected
        }

        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assert m_targetRepository.get().size() == 1 : "We expect to find exactly one target in the repository, but we find " + m_targetRepository.get().size();
        assert m_statefulTargetRepository.get().size() == 1 : "We expect to find exactly one stateful target in the repository, but we find " + m_statefulTargetRepository.get().size();

        // Removing stateful objects is now (partially) supported; see ACE-167 & ACE-230...
        m_statefulTargetRepository.remove(sgo);

        assert m_targetRepository.get().isEmpty() : "We expect to find no target in the repository, but we find " + m_targetRepository.get().size();
        assert m_statefulTargetRepository.get().isEmpty() : "We expect to find exactly no target in the repository, but we find " + m_statefulTargetRepository.get().size();
        
        cleanUp();
    }

    private void testStatefulAuditAndRemove() throws Exception {
        // preregister gateway
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "myNewGatewayA");
        final Map<String, String> tags = new HashMap<String, String>();

        final StatefulTargetObject sgo1 = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        // do checks
        assert sgo1.isRegistered() : "We just preregistered a gateway, so it should be registered.";

        // add auditlog data
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("myNewGatewayA", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        // do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a gateway does not influence its isRegistered().";
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's gatewayObject.";
        }
        // add auditlog data for other gateway
        events = new ArrayList<LogEvent>();
        props = new Properties();
        events.add(new LogEvent("myNewGatewayB", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.refresh();
                return false;
            }
        }, false, TOPIC_ADDED);
        final StatefulTargetObject sgo2 = findStatefulTarget("myNewGatewayB");

        // do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a gateway does not influence its isRegistered().";
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's gatewayObject.";
        }
        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getTargetObject();
            assert false : "We should not be able to get sgo2's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }
        // remove original gateway
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.remove(sgo1);
                return null;
            }
        }, true, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);

        // do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be registered.";
        try {
            sgo1.getTargetObject();
            assert false : "We should not be able to get sgo1's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getTargetObject();
            assert false : "We should not be able to get sgo2's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        // register second gateway
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo2.register();
                return null;
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        // do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be in registered.";
        try {
            sgo1.getTargetObject();
            assert false : "We should not be able to get sgo1's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assert sgo2.isRegistered() : "sgo2 has been registered.";
        try {
            sgo2.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo2's gatewayObject.";
        }

        int nrRegistered = m_statefulTargetRepository.get(m_bundleContext.createFilter("(" + KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")")).size();
        assert nrRegistered == 1 : "We expect to filter out one registered gateway, but we find " + nrRegistered;

        // Finally, refresh the repository; it should cause sgo1 to be re-created (due to its audit log)...
        // ACE-167 does not cover this scenario, but at a later time this should be fixed as well (see ACE-230).
        m_statefulTargetRepository.refresh();

        int count = m_statefulTargetRepository.get(m_bundleContext.createFilter("(" + KEY_ID + "=myNewGatewayA)")).size();
        assert count == 1 : "We expected sgo1 to be re-created!";

        cleanUp();
    }

    private void testStatefulApprove() throws Exception {
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "myNewTarget2");
        final Map<String, String> tags = new HashMap<String, String>();
        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assert !sgo.needsApprove() : "Without any deployment versions, and no information in the shop, we should not need to approve.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is "
            + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.New) : "We expect the registration state to be New, but it is "
            + sgo.getStoreState();
        assert sgo.getCurrentVersion().equals(UNKNOWN_VERSION);

        final ArtifactObject b11 = createBasicBundleObject("bundle1", "1", null);

        FeatureObject g1 = createBasicFeatureObject("feature1");
        FeatureObject g2 = createBasicFeatureObject("feature2"); // note that this feature is not associated to a bundle.

        createDynamicBundle2FeatureAssociation(b11, g1);

        final DistributionObject l1 = createBasicDistributionObject("distribution1");

        m_feature2distributionRepository.create(g1, l1);
        m_feature2distributionRepository.create(g2, l1);

        runAndWaitForEvent(new Callable<Distribution2TargetAssociation>() {
            public Distribution2TargetAssociation call() throws Exception {
                return m_distribution2targetRepository.create(l1, sgo.getTargetObject());
            }
        }, false, Distribution2TargetAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assert sgo.needsApprove() : "We added information that influences our target, so we should need to approve it.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is "
            + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Unapproved) : "We expect the registration state to be Unapproved, but it is "
            + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this target needs 1 bundle, but it states we need "
            + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromDeployment().length == 0 : "According to the deployment, this target needs 0 bundles, but it states we need "
            + sgo.getArtifactsFromDeployment().length;
        assert sgo.getCurrentVersion().equals(UNKNOWN_VERSION);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicDeploymentVersionObject("myNewTarget2", "1", b11);
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "We manually created a deployment version that reflects the shop, so no approval should be necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is "
            + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Approved) : "We expect the registration state to be Approved, but it is "
            + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this target needs 1 bundle, but it states we need "
            + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this target needs 1 bundles, but it states we need "
            + sgo.getArtifactsFromDeployment().length;

        runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle1", "2", null);
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2FeatureAssociation.TOPIC_CHANGED, TOPIC_STATUS_CHANGED);

        assert sgo.needsApprove() : "We added a new version of a bundle that is used by the target, so approval should be necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is "
            + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Unapproved) : "We expect the registration state to be Unapproved, but it is "
            + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this target needs 1 bundle, but it states we need "
            + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "The shop should tell use we need bundle URL 'bundle1-2', but it tells us we need "
            + sgo.getArtifactsFromShop()[0].getURL();
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this target needs 1 bundles, but it states we need "
            + sgo.getArtifactsFromDeployment().length;
        assert sgo.getArtifactsFromDeployment()[0].getUrl().equals("http://bundle1-1") : "The deployment should tell use we need bundle URL 'bundle1-1', but it tells us we need "
            + sgo.getArtifactsFromDeployment()[0].getUrl();
        assert sgo.getCurrentVersion().equals("1");

        final String newVersion = runAndWaitForEvent(new Callable<String>() {
            public String call() throws Exception {
                return sgo.approve();
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "Immediately after approval, no approval is necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is "
            + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Approved) : "We expect the registration state to be Approved, but it is "
            + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this target needs 1 bundle, but it states we need "
            + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "The shop should tell use we need bundle URL 'bundle1-2', but it tells us we need "
            + sgo.getArtifactsFromShop()[0].getURL();
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this target needs 1 bundles, but it states we need "
            + sgo.getArtifactsFromDeployment().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "Deployment should tell use we need bundle URL 'bundle1-2', but it tells us we need "
            + sgo.getArtifactsFromShop()[0].getURL();
        assert m_deploymentVersionRepository.get().size() == 2 : "We expect two deployment versions, but we find "
            + m_deploymentVersionRepository.get().size();
        assert sgo.getCurrentVersion().equals(newVersion);

        // clean up this object ourselves; we cannot rely on cleanUp() in this case.
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(sgo.getID());
                return null;
            }
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);

        assert m_statefulTargetRepository.get().size() == 0;
    }

    private void testStrangeNamesInTargets() throws InvalidSyntaxException, IOException {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();

        // add a target with a weird name.
        events.add(new LogEvent(":)", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        // see presence of sgo
        int sgrSizeBefore = m_statefulTargetRepository.get().size();
        m_statefulTargetRepository.refresh();
        assert m_statefulTargetRepository.get().size() == sgrSizeBefore + 1 : "After refresh, we expect "
            + (sgrSizeBefore + 1) + " target based on auditlogdata, but we find "
            + m_statefulTargetRepository.get().size();
        StatefulTargetObject sgo = findStatefulTarget(":)");
        sgo.register();
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "After registring our target, we assume it to be registered.";
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is "
            + m_statefulTargetRepository.get().get(0).getProvisioningState();

    }

    private void testStatefulAuditlog() throws IOException, InvalidSyntaxException {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("myTarget", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        // see presence of sgo
        assert m_statefulTargetRepository.get().size() == 0 : "Before audit log refresh, we expect nothing in the stateful repository, but we find "
            + m_statefulTargetRepository.get().size();
        m_statefulTargetRepository.refresh();
        assert m_statefulTargetRepository.get().size() == 1 : "After refresh, we expect 1 target based on auditlogdata, but we find "
            + m_statefulTargetRepository.get().size();
        StatefulTargetObject sgo = m_statefulTargetRepository.get().get(0);
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is "
            + m_statefulTargetRepository.get().get(0).getProvisioningState();

        // fill auditlog with complete-data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        events.add(new LogEvent("myTarget", 1, 2, 2, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        assert sgo.getLastInstallVersion().equals("123") : "Our last install version should be 123, but it is "
            + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.InProgress) : "We expect our object's provisioning state to be InProgress, but it is "
            + sgo.getProvisioningState();

        // fill auditlog with install data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        props.put(AuditEvent.KEY_SUCCESS, "false");
        events.add(new LogEvent("myTarget", 1, 3, 3, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        assert sgo.getLastInstallVersion().equals("123") : "Our last install version should be 123, but it is "
            + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.Failed) : "We expect our object's provisioning state to be Failed, but it is "
            + sgo.getProvisioningState();
        assert !sgo.getLastInstallSuccess() : "Our last install was not successful, but according to the sgo it was.";

        sgo.acknowledgeInstallVersion("123");
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is "
            + sgo.getProvisioningState();

        // add another install event.
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        events.add(new LogEvent("myTarget", 1, 4, 4, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        assert sgo.getLastInstallVersion().equals("124") : "Our last install version should be 124, but it is "
            + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.InProgress) : "We expect our object's provisioning state to be InProgress, but it is "
            + sgo.getProvisioningState();

        // fill auditlog with install data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        props.put(AuditEvent.KEY_SUCCESS, "true");
        events.add(new LogEvent("myTarget", 1, 5, 5, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        assert sgo.getLastInstallVersion().equals("124") : "Our last install version should be 124, but it is "
            + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.OK) : "We expect our object's provisioning state to be OK, but it is "
            + sgo.getProvisioningState();
        assert sgo.getLastInstallSuccess() : "Our last install was successful, but according to the sgo it was not.";

        sgo.acknowledgeInstallVersion("124");
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is "
            + sgo.getProvisioningState();
    }

    private void testStatefulAuditAndRegister() throws Exception {
        // preregister target
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "myNewTarget3");
        final Map<String, String> tags = new HashMap<String, String>();

        final StatefulTargetObject sgo1 = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        // do checks
        assert sgo1.isRegistered() : "We just preregistered a target, so it should be registered.";

        // add auditlog data
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("myNewTarget3", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        // do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a target does not influence its isRegistered().";
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's targetObject.";
        }

        // add auditlog data for other target
        events = new ArrayList<LogEvent>();
        props = new Properties();
        events.add(new LogEvent("myNewTarget4", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.refresh();
                return false;
            }
        }, false, TOPIC_ADDED);
        final StatefulTargetObject sgo2 = findStatefulTarget("myNewTarget4");

        // do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a target does not influence its isRegistered().";
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's targetObject.";
        }
        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getTargetObject();
            assert false : "We should not be able to get sgo2's targetObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        // remove original target
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(sgo1.getID());
                return null;
            }
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_STATUS_CHANGED);

        // do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be registered.";
        try {
            sgo1.getTargetObject();
            assert false : "We should not be able to get sgo1's targetObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getTargetObject();
            assert false : "We should not be able to get sgo2's targetObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        // register second target
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo2.register();
                return null;
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        // do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be in registered.";
        try {
            sgo1.getTargetObject();
            assert false : "We should not be able to get sgo1's targetObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assert sgo2.isRegistered() : "sgo2 has been registered.";
        try {
            sgo2.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo2's targetObject.";
        }

        int nrRegistered =
            m_statefulTargetRepository.get(
                m_bundleContext.createFilter("(" + KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")"))
                .size();
        assert nrRegistered == 1 : "We expect to filter out one registered target, but we find " + nrRegistered;

        // Finally, create a distribution object
        final DistributionObject l1 = createBasicDistributionObject("thedistribution");

        assert !sgo1.isRegistered() : "We just created a Staful GW object, is should not be registered";

        // register sgo1 again and create an association in 1 go
        Distribution2TargetAssociation lgw1 = runAndWaitForEvent(new Callable<Distribution2TargetAssociation>() {
            public Distribution2TargetAssociation call() throws Exception {
                sgo1.register();
                return m_distribution2targetRepository.create(l1, sgo1.getTargetObject());
            }
        }, false, Distribution2TargetAssociation.TOPIC_ADDED, TargetObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        // checks
        nrRegistered =
            m_statefulTargetRepository.get(
                m_bundleContext.createFilter("(" + KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")"))
                .size();
        assert nrRegistered == 2 : "We expect to filter out two registered targets, but we find " + nrRegistered;
        assert sgo1.isRegistered() : "A stateful gw object should be registered";
        assert sgo1.isAssociated(l1, DistributionObject.class) : "The stateful gw object should be associated to thedistribution.";
        assert lgw1.isSatisfied() : "Both ends of distribution - stateful gw should be satisfied.";
        
        cleanUp();
    }

    private StatefulTargetObject findStatefulTarget(String targetID) throws InvalidSyntaxException {
        for (StatefulTargetObject sgo : m_statefulTargetRepository.get()) {
            if (sgo.getID().equals(targetID)) {
                return sgo;
            }
        }
        return null;
    }

    @Test
    public void testStatefulApprovalWithArtifacts() throws Exception {
        // some setup: we need a helper.
        ArtifactHelper myHelper = new MockArtifactHelper("mymime");

        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Component myHelperService = m_dependencyManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(myHelper);

        m_dependencyManager.add(myHelperService);

        // Empty tag map to be reused througout test
        final Map<String, String> tags = new HashMap<String, String>();

        // First, create a bundle and two artifacts, but do not provide a processor for the artifacts.
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_URL, "http://myobject");
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, "my.processor.pid");
        attr.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        ArtifactObject a1 = m_artifactRepository.create(attr, tags);

        attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_URL, "http://myotherobject");
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, "my.processor.pid");
        attr.put(ArtifactObject.KEY_RESOURCE_ID, "mymime");
        attr.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        ArtifactObject a2 = m_artifactRepository.create(attr, tags);

        FeatureObject g = createBasicFeatureObject("feature");
        DistributionObject l = createBasicDistributionObject("distribution");

        attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, "myTarget");

        StatefulTargetObject sgo = m_statefulTargetRepository.preregister(attr, tags);

        m_artifact2featureRepository.create(b1, g);
        m_artifact2featureRepository.create(a1, g);
        m_artifact2featureRepository.create(a2, g);

        m_feature2distributionRepository.create(g, l);

        m_distribution2targetRepository.create(l, sgo.getTargetObject());

        try {
            sgo.approve();
            assert false : "Without a resource processor for our artifact, approve should go wrong.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        // Now, add a processor for the artifact.
        attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_URL, "http://myprocessor");
        attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "my.processor.pid");
        attr.put(BundleHelper.KEY_SYMBOLICNAME, "my.processor.bundle");
        attr.put(ArtifactHelper.KEY_MIMETYPE, BundleHelper.MIMETYPE);

        ArtifactObject b2 = m_artifactRepository.create(attr, tags);

        sgo.approve();

        DeploymentVersionObject dep = m_deploymentVersionRepository.getMostRecentDeploymentVersion(sgo.getID());

        DeploymentArtifact[] toDeploy = dep.getDeploymentArtifacts();

        assert toDeploy.length == 4 : "We expect to find four artifacts to deploy, but we find: " + toDeploy.length;
        DeploymentArtifact bundle1 = toDeploy[0];
        Assert.assertEquals(b1.getURL(), bundle1.getUrl());
        DeploymentArtifact bundle2 = toDeploy[1];
        Assert.assertEquals(b2.getURL(), bundle2.getUrl());
        Assert.assertEquals("true", bundle2.getDirective(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER));
        DeploymentArtifact artifact1 = toDeploy[2];
        Assert.assertEquals(a1.getURL(), artifact1.getUrl());
        Assert.assertEquals("my.processor.pid", artifact1.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
        DeploymentArtifact artifact2 = toDeploy[3];
        Assert.assertEquals(a2.getURL(), artifact2.getUrl());
        Assert.assertEquals("my.processor.pid", artifact2.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
        Assert.assertEquals(a2.getResourceId(), artifact2.getDirective(DeploymentArtifact.DIRECTIVE_KEY_RESOURCE_ID));

        cleanUp();

        m_dependencyManager.remove(myHelperService);
    }

    /*
     * The auto target operator is not yet session-aware; therefore, this test will fail. We should decide what
     * to do with this operator.
     */
// @Test
    public void testAutoTargetOperator() throws Exception {
        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        // configure automation bundle; new configuration properties; bundle will start
        final Properties props = new Properties();
        props.put("registerTargetFilter", "(id=anotherGate*)");
        props.put("approveTargetFilter", "(id=DO_NOTHING)");
        props.put("autoApproveTargetFilter", "(id=anotherGate*)");
        props.put("commitRepositories", "true");
        props.put("targetRepository", "target");
        props.put("deploymentRepository", "deployment");
        props.put("storeRepository", "store");
        props.put("customerName", "apache");
        props.put("hostName", HOST);
        props.put("endpoint", ENDPOINT);

        final Configuration config = m_configAdmin.getConfiguration("org.apache.ace.client.automation", null);

        /*
         * First test the basic scenario where we create some auditlog data, this target should be auto-registered after max 1 sec.
         */
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                config.update(props);
                return null;
            }
        }, true, RepositoryAdmin.TOPIC_LOGIN);

        testAutoTargetReg();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                config.delete();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);

        // Remove all repositories
        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
        }

        cleanUp();
    }

    private void testAutoTargetReg() throws Exception {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("anotherTarget", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        int initRepoSize = m_statefulTargetRepository.get().size();

        // Get the processauditlog task and run it
        ServiceTracker tracker =
            new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(&(" + Constants.OBJECTCLASS + "="
                + Runnable.class.getName() + ")(" + SchedulerConstants.SCHEDULER_NAME_KEY + "="
                + "org.apache.ace.client.processauditlog" + "))"), null);
        tracker.open();

        final Runnable processAuditlog = (Runnable) tracker.waitForService(2000);

        if (processAuditlog != null) {
            // commit should be called
            runAndWaitForEvent(new Callable<Object>() {
                public Object call() throws Exception {
                    processAuditlog.run();
                    return null;
                }
            }, false, RepositoryAdmin.TOPIC_REFRESH);

            assert m_statefulTargetRepository.get().size() == initRepoSize + 1 : "After refresh, we expect 1 target based on auditlogdata, but we find "
                + m_statefulTargetRepository.get().size();
            List<StatefulTargetObject> sgoList =
                m_statefulTargetRepository.get(m_bundleContext.createFilter("(id=anotherG*)"));
            StatefulTargetObject sgo = sgoList.get(0);
            assert sgo != null : "Expected one (anotherTarget) in the list.";

            // should be registered and auto approved
            assert sgo.isRegistered() : "The automation gw operator should have registered anotherTarget.";
            assert sgo.getAutoApprove() : "The automation gw operator should have auto-approved anotherTarget.";

            // add a target which will not be autoregistered
            events.clear();
            events.add(new LogEvent("secondTarget", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
            m_auditLogStore.put(events);

            // do auto target action
            processAuditlog.run();
            assert m_statefulTargetRepository.get().size() == initRepoSize + 2 : "After refresh, we expect an additional target based on auditlogdata, but we find "
                + m_statefulTargetRepository.get().size();
            sgoList = m_statefulTargetRepository.get(m_bundleContext.createFilter("(id=second*)"));
            sgo = sgoList.get(0);

            // second target should not be registered
            assert !sgo.isRegistered() : "The automation gw operator should not have registered secongTarget.";
            assert !sgo.getAutoApprove() : "The automation gw operator should not have auto-approved myTarget.";
        }
        else
        {
            assert false : "Could not get a reference to the processAuditLog task.";
        }
    }

    @Test
    public void testImportArtifactInvalidURL() throws Exception {
        try {
            m_artifactRepository.importArtifact(null, true);
            assert false : "A null URL cannot be imported into an artifact repository.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        URL invalidfile = new URL("file:/thisfilecannotexist");

        try {
            m_artifactRepository.importArtifact(invalidfile, true);
            assert false : "An illegal URL should result in an error.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        cleanUp();
    }

    @Test
    public void testImportArtifactGeneralBundle() throws Exception {
        // Use a valid JAR file, without a Bundle-SymbolicName header.
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1");
        attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");

        File temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), true);
            assert false : "Without a Bundle-SymbolicName header, the BundleHelper cannot recognize this bundle.";
        }
        catch (IllegalArgumentException re) {
            // expected
        }

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), "notTheBundleMime", true);
            assert false : "We have given an illegal mimetype, so no recognizer or helper can be found.";
        }
        catch (IllegalArgumentException re) {
            // expected
        }

        // Use a valid JAR file, with a Bundle-SymbolicName header, but do not supply an OBR.
        attributes.putValue(BundleHelper.KEY_SYMBOLICNAME, "org.apache.ace.test");

        temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), true);
            assert false : "No OBR has been started, so the artifact repository should complain that there is no storage available.";
        }
        catch (IOException ise) {
            // expected
        }

        // Supply the OBR.
        addObr("/obr", "store");
        m_artifactRepository.setObrBase(new URL("http://localhost:" + TestConstants.PORT + "/obr/"));

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assert m_artifactRepository.get().size() == 1;
        assert m_artifactRepository.getResourceProcessors().size() == 0;

        // Create a JAR file which looks like a resource processor supplying bundle.
        attributes.putValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "someProcessor");
        attributes.putValue(BundleHelper.KEY_VERSION, "1.0.0.processor");

        temp = File.createTempFile("org.apache.ace.test", ".jar");
        temp.deleteOnExit();
        jos = new JarOutputStream(new FileOutputStream(temp), manifest);
        jos.close();

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assert m_artifactRepository.get().size() == 1;
        assert m_artifactRepository.getResourceProcessors().size() == 1;

        deleteObr("/obr");
    }

    private class MockUser implements User {

        private final String m_name;

        public MockUser(String name) {
            m_name = name;
        }

        public Dictionary getCredentials() {
            return new Properties();
        }

        public boolean hasCredential(String arg0, Object arg1) {
            return false;
        }

        public String getName() {
            return m_name;
        }

        public Dictionary getProperties() {
            return new Properties();
        }

        public int getType() {
            return 0;
        }
    }

    @Test
    public void testLoginLogoutAndLoginOnceAgainWhileCreatingAnAssociation() throws IOException, InterruptedException,
        InvalidSyntaxException {
        User user1 = new MockUser("user1");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target").setWriteable());

        m_repositoryAdmin.login(loginContext1);

        FeatureObject g1 = createBasicFeatureObject("feature1");
        DistributionObject l1 = createBasicDistributionObject("distribution1");

        m_feature2distributionRepository.create(g1, l1);

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        try {
            removeAllRepositories();
        }
        catch (IOException ioe) {
            // too bad.
        }
    }

    /**
     * Tests read only repository access: marking a repository as readonly for a login should
     * mean that it does not get committed, but local changes will stay around between logins.
     */
    @Test
    public void testReadOnlyRepositoryAccess() throws Exception {
        User user1 = new MockUser("user1");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1
            .add(loginContext1.createShopRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext1.createTargetRepositoryContext()
                .setLocation(new URL(HOST + ENDPOINT)).setCustomer("apache").setName("target"));

        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        createBasicFeatureObject("feature1");
        createBasicTargetObject("target1");

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        assert m_featureRepository.get().size() == 1 : "We expect our own feature object in the repository; we find "
            + m_featureRepository.get().size();
        assert m_targetRepository.get().size() == 1 : "We expect our own target object in the repository; we find "
            + m_targetRepository.get().size();

        m_repositoryAdmin.commit();

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        assert m_featureRepository.get().size() == 1 : "We expect our own feature object in the repository; we find "
            + m_featureRepository.get().size();
        assert m_targetRepository.get().size() == 0 : "Since the target repository will not be committed, we expect no target objects in the repository; we find "
            + m_targetRepository.get().size();

        cleanUp();
        try {
            removeAllRepositories();
        }
        catch (IOException ioe) {
            // too bad.
        }
    }

    /**
     * Tests the template processing mechanism: given a custom processor, do the correct calls go out?
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTemplateProcessingInfrastructure() throws Exception {
        // create a preprocessor
        MockArtifactPreprocessor preprocessor = new MockArtifactPreprocessor();

        // create a helper
        MockArtifactHelper helper = new MockArtifactHelper("mymime", preprocessor);

        // register preprocessor and helper
        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Component helperService = m_dependencyManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(helper);

        m_dependencyManager.add(helperService);

        // create some tree from artifacts to a target
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                ArtifactObject b1 = createBasicBundleObject("myBundle");
                createBasicBundleObject("myProcessor", "1.0.0", "myProcessor.pid");
                ArtifactObject a1 = createBasicArtifactObject("myArtifact", "mymime", "myProcessor.pid");
                FeatureObject go = createBasicFeatureObject("myfeature");
                DistributionObject lo = createBasicDistributionObject("mydistribution");
                TargetObject gwo = createBasicTargetObject("templatetarget");
                m_artifact2featureRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_artifact2featureRepository.create(a1, go);
                m_feature2distributionRepository.create(go, lo);
                m_distribution2targetRepository.create(lo, gwo);
                return null;
            }
        }, false, TOPIC_ADDED);

        StatefulTargetObject sgo =
            m_statefulTargetRepository
                .get(m_bundleContext.createFilter("(" + TargetObject.KEY_ID + "=templatetarget)")).get(0);

        // wait until needsApprove is true; depending on timing, this could have happened before or after the TOPIC_ADDED.
        int attempts = 0;
        while (!sgo.needsApprove() && (attempts < 10)) {
            Thread.sleep(10);
        }
        assert sgo.needsApprove() : "With the new assignments, the SGO should need approval.";
        // create a deploymentversion
        sgo.approve();

// // the preprocessor now has gotten its properties; inspect these
        PropertyResolver target = preprocessor.getProps();
        assert target.get("id").equals("templatetarget") : "The property resolver should be able to resolve 'id'.";
        assert target.get("name").equals("mydistribution") : "The property resolver should be able to resolve 'name'.";
        assert target.get("someunknownproperty") == null : "The property resolver should not be able to resolve 'someunknownproperty'.";

        cleanUp(); // we need to do this before the helper goes away

        m_dependencyManager.remove(helperService);
    }

    /**
     * Tests the full template mechanism, from importing templatable artifacts, to creating deployment
     * versions with it. It uses the configuration (autoconf) helper, which uses a VelocityBased preprocessor.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTemplateProcessing() throws Exception {
        addObr("/obr", "store");
        m_artifactRepository.setObrBase(new URL("http://localhost:" + TestConstants.PORT + "/obr/"));

        // create some template things
        String xmlHeader =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<metatype:MetaData xmlns:metatype= \"http://www.osgi.org/xmlns/metatype/v1.0.0\">\n";
        String xmlFooter = "\n</metatype:MetaData>";

        String noTemplate = "<Attribute content=\"http://someURL\"/>";
        String noTemplateProcessed = "<Attribute content=\"http://someURL\"/>";
        final File noTemplateFile = createFileWithContents("template", ".xml", xmlHeader + noTemplate + xmlFooter);

        String simpleTemplate = "<Attribute content=\"http://$context.name\"/>";
        String simpleTemplateProcessed = "<Attribute content=\"http://mydistribution\"/>";
        File simpleTemplateFile = createFileWithContents("template", "xml", xmlHeader + simpleTemplate + xmlFooter);

        // create some tree from artifacts to a target
        FeatureObject go = runAndWaitForEvent(new Callable<FeatureObject>() {
            public FeatureObject call() throws Exception {
                ArtifactObject b1 = createBasicBundleObject("myBundle");
                createBasicBundleObject("myProcessor", "1.0.0", "org.osgi.deployment.rp.autoconf");
                FeatureObject go = createBasicFeatureObject("myfeature");
                DistributionObject lo = createBasicDistributionObject("mydistribution");
                TargetObject gwo = createBasicTargetObject("templatetarget2");
                m_artifact2featureRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_feature2distributionRepository.create(go, lo);
                m_distribution2targetRepository.create(lo, gwo);
                return go;
            }
        }, false, TOPIC_ADDED);

        ArtifactObject a1 = m_artifactRepository.importArtifact(noTemplateFile.toURI().toURL(), true);
        Artifact2FeatureAssociation a2g = m_artifact2featureRepository.create(a1, go);

        final StatefulTargetObject sgo =
            m_statefulTargetRepository.get(
                m_bundleContext.createFilter("(" + TargetObject.KEY_ID + "=templatetarget2)")).get(0);

        // create a deploymentversion
        assert sgo.needsApprove() : "With the new assignments, the SGO should need approval.";
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo.approve();
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED);

        // find the deployment version
        DeploymentVersionObject dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templatetarget2");
        String inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assert inFile.equals(xmlHeader + noTemplateProcessed + xmlFooter) : "We expected to find\n" + xmlHeader
            + noTemplateProcessed + xmlFooter + "\n in the processed artifact, but found\n" + inFile;

        // try the simple template
        m_artifact2featureRepository.remove(a2g);
        a1 = m_artifactRepository.importArtifact(simpleTemplateFile.toURI().toURL(), true);
        a2g = m_artifact2featureRepository.create(a1, go);

        sgo.approve();

        // find the deployment version
        dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templatetarget2");
        // sleep for a while, to allow the OBR to process the file.
        Thread.sleep(1000);

        inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assert inFile.equals(xmlHeader + simpleTemplateProcessed + xmlFooter) : "We expected to find\n" + xmlHeader
            + simpleTemplateProcessed + xmlFooter + "\n in the processed artifact, but found\n" + inFile;

        deleteObr("/obr");
    }

    private String tryGetStringFromURL(URL url, int tries, int interval) throws Exception {
        while (true) {
            try {
                String result = getStringFromURL(url);
                return result;
            }
            catch (IOException ioe) {
                Thread.sleep(interval);
                tries--;
                if (tries == 0) {
                    throw ioe;
                }
            }
        }

    }

    /**
     * Helper method for testTemplateProcessing; finds the URL of the first deploymentartifact
     * with 'xml' in its url.
     */
    private URL findXmlUrlInDeploymentObject(DeploymentVersionObject dvo) throws MalformedURLException {
        DeploymentArtifact[] artifacts = dvo.getDeploymentArtifacts();
        for (DeploymentArtifact da : artifacts) {
            if (da.getUrl().contains("xml")) {
                return new URL(da.getUrl());
            }
        }
        return null;
    }

    /**
     * Creates a temporary file with the given name and extension, and stores the given
     * contents in it.
     */
    private File createFileWithContents(String name, String extension, String contents) throws IOException {
        File file = File.createTempFile(name, extension);
        file.deleteOnExit();
        Writer w = new OutputStreamWriter(new FileOutputStream(file));
        w.write(contents);
        w.close();
        return file;
    }

    /**
     * Opens a URL, and gets all data from it as a string.
     */
    private String getStringFromURL(URL url) throws IOException {
        StringBuilder found = new StringBuilder();
        Reader reader = new InputStreamReader(url.openStream());

        char[] buf = new char[1024];
        for (int count = reader.read(buf); count != -1; count = reader.read(buf)) {
            found.append(buf, 0, count);
        }
        reader.close();
        return found.toString();
    }

    private volatile List<String> m_waitingForTopic = Collections.synchronizedList(new ArrayList<String>());
    private volatile Semaphore m_semaphore;
    private volatile boolean m_runAndWaitDebug = false;

    private <T> T runAndWaitForEvent(Callable<T> callable, boolean debug, String... topic) throws Exception {
        m_runAndWaitDebug = debug;
        T result = null;
        m_waitingForTopic.clear();
        m_waitingForTopic.addAll(Arrays.asList(topic));
        m_semaphore = new Semaphore(0);
        result = callable.call();
        assert m_semaphore.tryAcquire(15000, TimeUnit.MILLISECONDS) : "We expect the event within a reasonable timeout.";
        m_semaphore = null;
        return result;
    }

    public void handleEvent(Event event) {
        if (m_runAndWaitDebug) {
            System.err.println("Received event: " + event.getTopic());
        }
        if (m_waitingForTopic.remove(event.getTopic())) {
            if (m_runAndWaitDebug) {
                System.err.println("Event was expected.");
            }
            if ((m_semaphore != null) && m_waitingForTopic.isEmpty()) {
                m_semaphore.release();
                m_runAndWaitDebug = false;
            }
        }
    }

    private ArtifactObject createBasicBundleObject(String symbolicName) {
        return createBasicBundleObject(symbolicName, null, null);
    }

    private ArtifactObject createBasicBundleObject(String symbolicName, String version, String processorPID) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(BundleHelper.KEY_SYMBOLICNAME, symbolicName);
        attr.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        attr.put(ArtifactObject.KEY_URL, "http://" + symbolicName + "-" + ((version == null) ? "null" : version));
        Map<String, String> tags = new HashMap<String, String>();

        if (version != null) {
            attr.put(BundleHelper.KEY_VERSION, version);
        }
        if (processorPID != null) {
            attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, processorPID);
        }
        return m_artifactRepository.create(attr, tags);
    }

    private ArtifactObject createBasicArtifactObject(String name, String mimetype, String processorPID)
        throws InterruptedException {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        attr.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        attr.put(ArtifactObject.KEY_URL, "http://" + name);
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, processorPID);
        Map<String, String> tags = new HashMap<String, String>();

        return m_artifactRepository.create(attr, tags);
    }

    private FeatureObject createBasicFeatureObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(FeatureObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_featureRepository.create(attr, tags);
    }

    private DistributionObject createBasicDistributionObject(String name) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DistributionObject.KEY_NAME, name);
        Map<String, String> tags = new HashMap<String, String>();

        return m_distributionRepository.create(attr, tags);
    }

    private TargetObject createBasicTargetObject(String id) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(TargetObject.KEY_ID, id);
        Map<String, String> tags = new HashMap<String, String>();

        return m_targetRepository.create(attr, tags);
    }

    private DeploymentVersionObject createBasicDeploymentVersionObject(String targetID, String version,
        ArtifactObject... bundles) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DeploymentVersionObject.KEY_TARGETID, targetID);
        attr.put(DeploymentVersionObject.KEY_VERSION, version);
        Map<String, String> tags = new HashMap<String, String>();

        List<DeploymentArtifact> artifacts = new ArrayList<DeploymentArtifact>();
        for (ArtifactObject artifact : bundles) {
            Map<String, String> directives = new HashMap<String, String>();
            directives.put(BundleHelper.KEY_SYMBOLICNAME, artifact.getAttribute(BundleHelper.KEY_SYMBOLICNAME));
            directives.put(DeploymentArtifact.DIRECTIVE_KEY_BASEURL, artifact.getURL());
            if (artifact.getAttribute(BundleHelper.KEY_VERSION) != null) {
                directives.put(BundleHelper.KEY_VERSION, artifact.getAttribute(BundleHelper.KEY_VERSION));
            }
            artifacts.add(m_deploymentVersionRepository.createDeploymentArtifact(artifact.getURL(), directives));
        }
        return m_deploymentVersionRepository.create(attr, tags, artifacts.toArray(new DeploymentArtifact[0]));
    }

    private Artifact2FeatureAssociation createDynamicBundle2FeatureAssociation(ArtifactObject artifact,
        FeatureObject feature) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
        return m_artifact2featureRepository.create(artifact, properties, feature, null);
    }

    /*
     * The following code is borrowed from RepositoryTest.java, and is used to instantiate and
     * use repository servlets.
     */

    protected void startRepositoryService() throws IOException {
        // configure the (replication)repository servlets
        configure("org.apache.ace.repository.servlet.RepositoryServlet", HttpConstants.ENDPOINT,
            ENDPOINT, "authentication.enabled", "false");
    }

    @After
    public void tearDown() throws Exception {
        // remove all repositories, in case a test case does not reach it's cleanup section due to an exception
        removeAllRepositories();
    }

    /* Configure a new repository instance */
    private void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException,
        InterruptedException, InvalidSyntaxException {
        // Publish configuration for a repository instance
        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, customer);
        props.put(RepositoryConstants.REPOSITORY_NAME, name);
        props.put(RepositoryConstants.REPOSITORY_MASTER, String.valueOf(isMaster));
        props.put("factory.instance.pid", instanceName);
        Configuration config =
            m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        ServiceTracker tracker =
            new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName
                + ")"), null);
        tracker.open();

        config.update(props);

        if (tracker.waitForService(5000) == null) {
            throw new IOException("Did not get notified about new repository becoming available in time.");
        }
        tracker.close();
    }

    private void addObr(String endpoint, String fileLocation) throws IOException, InterruptedException {
        configure("org.apache.ace.obr.servlet", "OBRInstance", "singleOBRServlet", "org.apache.ace.server.servlet.endpoint", endpoint, "authentication.enabled", "false");
        configure("org.apache.ace.obr.storage.file", "OBRInstance", "singleOBRStore", OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);

        // Wait for the endpoint to respond.
        // TODO below there is a similar url that does put a slash between port and endpoint, why?
        URL url = new URL("http://localhost:" + TestConstants.PORT + endpoint + "/repository.xml");
        int response = ((HttpURLConnection) url.openConnection()).getResponseCode();
        int tries = 0;
        while ((response != 200) && (tries < 50)) {
            Thread.sleep(100); // If we get interrupted, there will be a good reason for it.
            response = ((HttpURLConnection) url.openConnection()).getResponseCode();
            tries++;
        }
        if (tries == 50) {
            throw new IOException("The OBR servlet does not seem to be responding well. Last response code: "
                + response);
        }
    }

    private void deleteObr(String endpoint) throws IOException, InvalidSyntaxException, InterruptedException {
        // This is a little ugly: we cannot just delete the configuration, since that will result in a
        // sharing violation between this bundle and the servlet bundle. In stead, we make the servlet
        // use an invalid endpoint.
        Properties propsServlet = new Properties();
        propsServlet.put(HttpConstants.ENDPOINT, endpoint + "invalid");
        propsServlet.put("OBRInstance", "singleOBRServlet");
        Configuration configServlet = m_configAdmin.getConfiguration("org.apache.ace.obr.servlet");
        configServlet.update(propsServlet);

        URL url = new URL("http://localhost:" + TestConstants.PORT + "/" + endpoint + "/repository.xml");
        int response = ((HttpURLConnection) url.openConnection()).getResponseCode();
        int tries = 0;
        while ((response != 404) && (tries < 50)) {
            Thread.sleep(100); // If we get interrupted, there will be a good reason for it.
            response = ((HttpURLConnection) url.openConnection()).getResponseCode();
            tries++;
        }
        if (tries == 50) {
            throw new IOException("The OBR servlet does not want to go away. Last response code: " + response);
        }
    }

    private void removeAllRepositories() throws IOException, InvalidSyntaxException, InterruptedException {
        final Configuration[] configs = m_configAdmin.listConfigurations("(factory.instance.pid=*)");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);

            ServiceTracker tracker =
                new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(" + Constants.OBJECTCLASS + "="
                    + Repository.class.getName() + ")"), null) {
                    @Override
                    public void removedService(ServiceReference reference, Object service) {
                        super.removedService(reference, service);
                        // config.length times two because the service tracker also sees added events for each instance
                        if (size() == 0) {
                            sem.release();
                        }
                    }
                };
            tracker.open();

            for (int i = 0; i < configs.length; i++) {
                configs[i].delete();
            }

            if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new IOException("Not all instances were removed in time.");
            }
            tracker.close();
        }
    }
}

class MockArtifactHelper implements ArtifactHelper {
    private final String m_mimetype;
    private final ArtifactPreprocessor m_preprocessor;

    MockArtifactHelper(String mimetype) {
        this(mimetype, null);
    }

    MockArtifactHelper(String mimetype, ArtifactPreprocessor preprocessor) {
        m_mimetype = mimetype;
        m_preprocessor = preprocessor;
    }

    public boolean canUse(ArtifactObject object) {
        return object.getMimetype().equals(m_mimetype);
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        return attributes;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[] { ArtifactObject.KEY_URL };
    }

    public String[] getMandatoryAttributes() {
        return new String[] { ArtifactObject.KEY_URL };
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return ("(" + ArtifactObject.KEY_URL + "=" + obj.getURL() + ")");
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return 1;
    }

    public ArtifactPreprocessor getPreprocessor() {
        return m_preprocessor;
    }
};

class MockArtifactPreprocessor implements ArtifactPreprocessor {
    private PropertyResolver m_props;

    public String preprocess(String url, PropertyResolver props, String targetID, String version, URL obrBase)
        throws IOException {
        m_props = props;
        return url;
    }

    PropertyResolver getProps() {
        return m_props;
    }

    public boolean needsNewVersion(String url, PropertyResolver props, String targetID, String fromVersion) {
        return false;
    }
}
