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
package org.apache.ace.test.repositoryadmin;

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

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject.ProvisioningState;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject.RegistrationState;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject.StoreState;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogEvent;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.scheduler.constants.SchedulerConstants;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.TestUtils;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.service.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.useradmin.User;
import org.osgi.util.tracker.ServiceTracker;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class RepositoryAdminTest implements EventHandler {
    private volatile BundleContext m_context; /* Injected by dependency manager */
    private volatile ConfigurationAdmin m_configAdmin; /* Injected by dependency manager */
    private volatile DependencyManager m_depManager; /* injected by dependency manager */
    private volatile RepositoryAdmin m_repositoryAdmin; /* Injected by dependency manager */
    private volatile ArtifactRepository m_artifactRepository; /* Injected by dependency manager */
    private volatile Artifact2GroupAssociationRepository m_artifact2groupRepository; /* Injected by dependency manager */
    private volatile GroupRepository m_groupRepository; /* Injected by dependency manager */
    private volatile Group2LicenseAssociationRepository m_group2licenseRepository; /* Injected by dependency manager */
    private volatile LicenseRepository m_licenseRepository; /* Injected by dependency manager */
    private volatile License2GatewayAssociationRepository m_license2gatewayRepository; /* Injected by dependency manager */
    private volatile GatewayRepository m_gatewayRepository; /* Injected by dependency manager */
    private volatile DeploymentVersionRepository m_deploymentVersionRepository; /* Injected by dependency manager */
    private volatile StatefulGatewayRepository m_statefulGatewayRepository; /* Injected by dependency manager */
    private volatile LogStore m_auditLogStore; /* Injected by dependency manager */

    /*
     * TestNG magic
     */
    private static Object instance;
    public RepositoryAdminTest() {
        if (instance == null) {
            instance = this;
        }
    }

    @Factory
    public Object[] createInstances() {
        return new Object[] { instance };
    }

    @AfterMethod( alwaysRun = true )
    public void cleanUp() throws IOException, InvalidSyntaxException, InterruptedException {
        // Simply remove all objects in the repository.
        clearRepository(m_artifactRepository);
        clearRepository(m_artifact2groupRepository);
        clearRepository(m_group2licenseRepository);
        clearRepository(m_license2gatewayRepository);
        clearRepository(m_artifactRepository);
        clearRepository(m_groupRepository);
        clearRepository(m_licenseRepository);
        clearRepository(m_gatewayRepository);
        clearRepository(m_deploymentVersionRepository);
        m_statefulGatewayRepository.refresh();
        try {
            m_repositoryAdmin.logout(true);
        }
        catch (Exception ioe) {
            //ioe.printStackTrace(System.out);
        }
    }

    public <T extends RepositoryObject> void clearRepository (ObjectRepository<T> rep) {
        for (T entity : rep.get()) {
            rep.remove(entity);
        }
        assert rep.get().size() == 0 : "Something went wrong clearing the repository.";
    }

    /**
     * Add a bundle, group and license, associate all, remove the group, No associations should be left.
     * @throws Exception
     */
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testRemoveBundleGroup() throws Exception {
        final ArtifactObject b1 = createBasicBundleObject("thebundle","1", null);
        final GroupObject g1 = createBasicGroupObject("thegroup");

        final Artifact2GroupAssociation bg = runAndWaitForEvent(new Callable<Artifact2GroupAssociation>() {
            public Artifact2GroupAssociation call() throws Exception {
                return m_artifact2groupRepository.create("(&(" + BundleHelper.KEY_SYMBOLICNAME + "=thebundle)(|("+BundleHelper.KEY_VERSION+">=1)("+BundleHelper.KEY_VERSION+"=<3))(!("+BundleHelper.KEY_VERSION+"=3)))", "(name=thegroup)");
            }
        }, false, Artifact2GroupAssociation.TOPIC_ADDED);

        final LicenseObject l1 = createBasicLicenseObject("thelicense");

        final Group2LicenseAssociation gtl = runAndWaitForEvent(new Callable<Group2LicenseAssociation>() {
            public Group2LicenseAssociation call() throws Exception {
                return m_group2licenseRepository.create("(name=thegroup)","(name=thelicense)");
            }
        }, false, Group2LicenseAssociation.TOPIC_ADDED);

        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b1) : "The left side of the BG-association should be b1.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the BG-association should be g1.";
        assert (gtl.getLeft().size() == 1) && gtl.getLeft().contains(g1) : "The left side of the GtL-association should be g1.";
        assert (gtl.getRight().size() == 1) && gtl.getRight().contains(l1) : "The right side of the GtL-association should be l1.";
        assert bg.isSatisfied() : "The bundlegroup association should be satisfied.";
        assert gtl.isSatisfied() : "The group2license association should be satisfied.";
        assert b1.getGroups().size() == 1 : "Bundle b1 should be associated to one group.";
        assert l1.getGroups().size() == 1 : "License l1 should be associated to one group.";

        //remove the group
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_groupRepository.remove(g1);
                return null;
            }
        }, false,Artifact2GroupAssociation.TOPIC_CHANGED, Group2LicenseAssociation.TOPIC_CHANGED);

        assert !gtl.isSatisfied() : "The bundlegroup association shouldn not be satisfied.";
        assert !bg.isSatisfied() : "The group2license assocation should not be satisfied.";

        assert b1.getGroups().size() == 0 : "Bundle b1 shouldn't be associated to any group, but is associated to " + b1.getGroups();
        assert l1.getGroups().size() == 0 : "License l1 shouldn't be associated to any group.";

        cleanUp();
    }


    @Test( groups = { TestUtils.INTEGRATION } )
    public void testAssociationsWithMovingEndpoints () throws Exception {
        final ArtifactObject b1 = createBasicBundleObject("thebundle", "1", null);
        final GroupObject g1 = createBasicGroupObject("thegroup");
        final Artifact2GroupAssociation bg = runAndWaitForEvent(new Callable<Artifact2GroupAssociation>() {
            public Artifact2GroupAssociation call() throws Exception {
                Map<String, String> properties = new HashMap<String, String>();
                properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "[1,3)");
                return m_artifact2groupRepository.create(b1, properties, g1, null);
            }
        }, false, Artifact2GroupAssociation.TOPIC_ADDED);

        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b1) : "The left side of the association should now be b1; we find " + bg.getLeft().size() + " bundles on the left side of the association.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getGroups().get(0) == g1 : "b1 should be assocated with g1";
        assert g1.getArtifacts().get(0) == b1 : "g1 should be assocated with b1";

        final ArtifactObject b2 = runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("thebundle", "2", null);
            }
        }, false, Artifact2GroupAssociation.TOPIC_CHANGED);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1; we find " + bg.getLeft().size() + " bundles.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getGroups().size() == 0 : "b1 should not be associated with any group.";
        assert b2.getGroups().get(0) == g1 : "b2 should now be assocation with g1";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        ArtifactObject b3 = createBasicBundleObject("thebundle", "3", null);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getGroups().size() == 0 : "b1 should not be associated with any group.";
        assert b2.getGroups().get(0) == g1 : "b2 should now be assocation with g1";
        assert b3.getGroups().size() == 0 : "b3 should not be associated with any group.";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        ArtifactObject b15 = createBasicBundleObject("thebundle", "1.5", null);

        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b15) : "The left side of the association should not be b15.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b2) : "The left side of the association should now be b2.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getGroups().size() == 0 : "b1 should not be associated with any group.";
        assert b15.getGroups().size() == 0 : "b15 should not be associated with any group.";
        assert b2.getGroups().get(0) == g1 : "b2 should now be assocation with g1";
        assert b3.getGroups().size() == 0 : "b3 should not be associated with any group.";
        assert g1.getArtifacts().get(0) == b2 : "g1 should be assocation with b2";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_artifactRepository.remove(b2);
                return null;
            }
        }, false, Artifact2GroupAssociation.TOPIC_CHANGED);

        //note that we cannot test anything for b2: this has been removed, and now has no
        //defined state.
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b1) : "The left side of the association should no longer be b1.";
        assert (bg.getLeft().size() == 1) && bg.getLeft().contains(b15) : "The left side of the association should now be b15.";
        assert (bg.getLeft().size() == 1) && !bg.getLeft().contains(b3) : "The left side of the association should not be b3.";
        assert (bg.getRight().size() == 1) && bg.getRight().contains(g1) : "The right side of the association should now be g1.";
        assert b1.getGroups().size() == 0 : "b1 should not be associated with any group.";
        assert b15.getGroups().get(0) == g1 : "b15 should now be assocation with g1";
        assert b3.getGroups().size() == 0 : "b3 should not be associated with any group.";
        assert g1.getArtifacts().get(0) == b15 : "g1 should be assocation with b15";
        assert g1.getArtifacts().size() == 1 : "g1 should be associated with one bundle";

        cleanUp();
    }

    private static final String ENDPOINT = "/AdminRepTest";
    private static final String HOST = "http://localhost:" + TestConstants.PORT;

    /**
     * Tests the behavior with logging in and out (with multiple users), and communication
     * with the server.
     * @throws Exception
     */
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testRepositoryAdmin() throws Exception {
        final User user1 = new MockUser("user1");
        final User user2 = new MockUser("user2");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);

        try {
            m_repositoryAdmin.checkout();
            assert false : "Without being logged in, it should not be possible to do checkout.";
        }
        catch (IllegalStateException ise) {
            //expected
        }

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext1.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", true);
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
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.New) : "We expect the working state of our bundle to be New, but it is " + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 0 : "We expect 0 bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

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
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.New) : "We expect the working state of our bundle to be New, but it is " + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 0 : "We expect 0 bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

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
        loginContext2.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext2.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", true);

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

        assert m_artifactRepository.get().size() == 1 : "We expect to find 1 bundle after checkout, but we find " + m_artifactRepository.get().size();
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
        assert m_repositoryAdmin.getWorkingState(b1).equals(WorkingState.Unchanged) : "We expect the working state of our bundle1 to be Unchanged, but it is " + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getWorkingState(b2).equals(WorkingState.New) : "We expect the working state of our bundle2 to be New, but it is " + m_repositoryAdmin.getWorkingState(b1);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New) == 1 : "We expect one bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.New);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed) == 0 : "We expect 0 bundle object in working state Changed, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);
        assert m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Unchanged) == 1 : "We expect 1 bundle object in working state New, but we find "+ m_repositoryAdmin.getNumberWithWorkingState(ArtifactObject.class, WorkingState.Changed);

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

        assert m_artifactRepository.get().size() == 1 : "We expect 1 item in the bundle repository, in stead of " + m_artifactRepository.get().size();

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

        assert m_artifactRepository.get().size() == 2 : "We expect 2 items in the bundle repository, in stead of " + m_artifactRepository.get().size();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_repositoryAdmin.revert();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_REFRESH, RepositoryAdmin.TOPIC_STATUSCHANGED);

        assert m_artifactRepository.get().size() == 1 : "We expect 1 item in the bundle repository, in stead of " + m_artifactRepository.get().size();

        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
            e.printStackTrace(System.err);
        }

        cleanUp();
    }

    @Test( groups = { TestUtils.INTEGRATION } )
    public void testAutoApprove() throws Exception {
        User user = new MockUser("user");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", true);
        loginContext.addDeploymentRepository(new URL(HOST + ENDPOINT), "apache", "deployment", true);
        m_repositoryAdmin.login(loginContext);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicGatewayObject("testAutoApproveGateway");
                return null;
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_ADDED);

        final StatefulGatewayObject sgo = m_statefulGatewayRepository.get(m_context.createFilter("(" + GatewayObject.KEY_ID + "=" + "testAutoApproveGateway)")).get(0);

        // Set up some deployment information for the gateway.
        final GroupObject g = runAndWaitForEvent(new Callable<GroupObject>() {
            public GroupObject call() throws Exception {
                ArtifactObject b = createBasicBundleObject("myBundle", "1.0", null);
                GroupObject g = createBasicGroupObject("myGroup");
                LicenseObject l = createBasicLicenseObject("myLicense");
                m_artifact2groupRepository.create(b, g);
                m_group2licenseRepository.create(g, l);
                m_license2gatewayRepository.create(l, sgo.getGatewayObject());
                return g;
            }
        }, false, ArtifactObject.TOPIC_ADDED, GroupObject.TOPIC_ADDED, LicenseObject.TOPIC_ADDED,
                  Artifact2GroupAssociation.TOPIC_ADDED, Group2LicenseAssociation.TOPIC_ADDED,
                  License2GatewayAssociation.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert sgo.needsApprove() : "We added some deployment information, so the gateway should need approval.";

        sgo.setAutoApprove(true);

        assert sgo.needsApprove() : "Turning on the autoapprove should not automatically approve whatever was waiting.";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo.approve();
                return null;
            }
        }, false, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "We approved the new version by hand, so we should not need approval.";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                ArtifactObject b = createBasicBundleObject("myBundle2", "1.0", null);
                m_artifact2groupRepository.create(b, g);
                return null;
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2GroupAssociation.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

      assert !sgo.needsApprove() : "With autoapprove on, adding new deployment information should still not need approval (at least, after the two CHANGED events).";

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.unregister(sgo.getID());
                return null;
            }
        }, false, StatefulGatewayObject.TOPIC_STATUS_CHANGED, StatefulGatewayObject.TOPIC_REMOVED);

        try {
            removeAllRepositories();
        }
        catch (Exception e) {
            // Not much we can do...
            e.printStackTrace(System.err);
        }
        cleanUp();
    }

    @Test( groups = { TestUtils.INTEGRATION } )
    public void testStateful() throws Exception {
        User user = new MockUser("user");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", true);
        loginContext.addDeploymentRepository(new URL(HOST + ENDPOINT), "apache", "deployment", true);
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
        testStrangeNamesInGateways();

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

        // register gateway with
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, "a_gateway");
        attr.put(GatewayObject.KEY_AUTO_APPROVE, String.valueOf(true));
        final Map<String, String> tags = new HashMap<String, String>();

        final StatefulGatewayObject sgo = runAndWaitForEvent(new Callable<StatefulGatewayObject>() {
            public StatefulGatewayObject call() throws Exception {
                return m_statefulGatewayRepository.preregister(attr, tags);
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_ADDED);

        assert m_gatewayRepository.get().size() == 1 : "We expect to find exactly one gateway in the repository, but we find " + m_gatewayRepository.get().size();
        assert m_statefulGatewayRepository.get().size() == 1 : "We expect to find exactly one stateful gateway in the repository, but we find " + m_statefulGatewayRepository.get().size();

        assert sgo.getAutoApprove() : "The gateway should have auto approved value: true but got: false.";

        sgo.setAutoApprove(false);

        assert !sgo.getAutoApprove() : "The gateway should have auto approved value: false but got: true.";

        //clean up
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.unregister(sgo.getID());
                return null;
            }
        }, false, GatewayObject.TOPIC_REMOVED, StatefulGatewayObject.TOPIC_REMOVED);
    }

    private void testStatefulCreateRemove() throws Exception {
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, "myNewGateway1");
        final Map<String, String> tags = new HashMap<String, String>();

        try {
            m_statefulGatewayRepository.create(attr, tags);
            assert false : "Creating a stateful gateway repository should not be allowed.";
        }
        catch (UnsupportedOperationException uoe) {
            // expected
        }

        final StatefulGatewayObject sgo = runAndWaitForEvent(new Callable<StatefulGatewayObject>() {
            public StatefulGatewayObject call() throws Exception {
                return m_statefulGatewayRepository.preregister(attr, tags);
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_ADDED);

        assert m_gatewayRepository.get().size() == 1 : "We expect to find exactly one gateway in the repository, but we find " + m_gatewayRepository.get().size();
        assert m_statefulGatewayRepository.get().size() == 1 : "We expect to find exactly one stateful gateway in the repository, but we find " + m_statefulGatewayRepository.get().size();

        try {
            m_statefulGatewayRepository.remove(sgo);
            assert false : "Deleting a stateful gateway repositoy should not be allowed.";
        }
        catch (UnsupportedOperationException uoe) {
            // expected
        }

        assert m_gatewayRepository.get().size() == 1 : "We expect to find exactly one gateway in the repository, but we find " + m_gatewayRepository.get().size();
        assert m_statefulGatewayRepository.get().size() == 1 : "We expect to find exactly one stateful gateway in the repository, but we find " + m_statefulGatewayRepository.get().size();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.unregister(sgo.getID());
                return null;
            }
        }, false, GatewayObject.TOPIC_REMOVED, StatefulGatewayObject.TOPIC_REMOVED);

        assert m_gatewayRepository.get().size() == 0 : "We expect to find no gateway in the repository, but we find " + m_gatewayRepository.get().size();
        assert m_statefulGatewayRepository.get().size() == 0 : "We expect to find no stateful gateway in the repository, but we find " + m_statefulGatewayRepository.get().size();
    }

    private void testStatefulApprove() throws Exception {
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, "myNewGateway2");
        final Map<String, String> tags = new HashMap<String, String>();
        final StatefulGatewayObject sgo = runAndWaitForEvent(new Callable<StatefulGatewayObject>() {
            public StatefulGatewayObject call() throws Exception {
                return m_statefulGatewayRepository.preregister(attr, tags);
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_ADDED);

        assert !sgo.needsApprove() : "Without any deployment versions, and no information in the shop, we should not need to approve.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is " + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.New) : "We expect the registration state to be New, but it is " + sgo.getStoreState();
        assert sgo.getCurrentVersion().equals(StatefulGatewayObject.UNKNOWN_VERSION);

        final ArtifactObject b11 = createBasicBundleObject("bundle1", "1", null);

        GroupObject g1 = createBasicGroupObject("group1");
        GroupObject g2 = createBasicGroupObject("group2"); // note that this group is not associated to a bundle.

        createDynamicBundle2GroupAssociation(b11, g1);

        final LicenseObject l1 = createBasicLicenseObject("license1");

        m_group2licenseRepository.create(g1, l1);
        m_group2licenseRepository.create(g2, l1);

        runAndWaitForEvent(new Callable<License2GatewayAssociation>() {
                public License2GatewayAssociation call() throws Exception {
                    return m_license2gatewayRepository.create(l1, sgo.getGatewayObject());
                }
            }, false, License2GatewayAssociation.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert sgo.needsApprove() : "We added information that influences our gateway, so we should need to approve it.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is " + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Unapproved) : "We expect the registration state to be Unapproved, but it is " + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this gateway needs 1 bundle, but it states we need " + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromDeployment().length == 0 : "According to the deployment, this gateway needs 0 bundles, but it states we need " + sgo.getArtifactsFromDeployment().length;
        assert sgo.getCurrentVersion().equals(StatefulGatewayObject.UNKNOWN_VERSION);

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicDeploymentVersionObject("myNewGateway2", "1", b11);
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "We manually created a deployment version that reflects the shop, so no approval should be necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is " + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Approved) : "We expect the registration state to be Approved, but it is " + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this gateway needs 1 bundle, but it states we need " + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this gateway needs 1 bundles, but it states we need " + sgo.getArtifactsFromDeployment().length;

        runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle1", "2", null);
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2GroupAssociation.TOPIC_CHANGED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert sgo.needsApprove() : "We added a new version of a bundle that is used by the gateway, so approval should be necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is " + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Unapproved) : "We expect the registration state to be Unapproved, but it is " + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this gateway needs 1 bundle, but it states we need " + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "The shop should tell use we need bundle URL 'bundle1-2', but it tells us we need " + sgo.getArtifactsFromShop()[0].getURL();
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this gateway needs 1 bundles, but it states we need " + sgo.getArtifactsFromDeployment().length;
        assert sgo.getArtifactsFromDeployment()[0].getUrl().equals("http://bundle1-1") : "The deployment should tell use we need bundle URL 'bundle1-1', but it tells us we need " + sgo.getArtifactsFromDeployment()[0].getUrl();
        assert sgo.getCurrentVersion().equals("1");

        final String newVersion = runAndWaitForEvent(new Callable<String>() {
            public String call() throws Exception {
                return sgo.approve();
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        assert !sgo.needsApprove() : "Immediately after approval, no approval is necessary.";
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "We expect the registration state to be Registered, but it is " + sgo.getRegistrationState();
        assert sgo.getStoreState().equals(StoreState.Approved) : "We expect the registration state to be Approved, but it is " + sgo.getStoreState();
        assert sgo.getArtifactsFromShop().length == 1 : "According to the shop, this gateway needs 1 bundle, but it states we need " + sgo.getArtifactsFromShop().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "The shop should tell use we need bundle URL 'bundle1-2', but it tells us we need " + sgo.getArtifactsFromShop()[0].getURL();
        assert sgo.getArtifactsFromDeployment().length == 1 : "According to the deployment, this gateway needs 1 bundles, but it states we need " + sgo.getArtifactsFromDeployment().length;
        assert sgo.getArtifactsFromShop()[0].getURL().equals("http://bundle1-2") : "Deployment should tell use we need bundle URL 'bundle1-2', but it tells us we need " + sgo.getArtifactsFromShop()[0].getURL();
        assert m_deploymentVersionRepository.get().size() == 2 : "We expect two deployment versions, but we find " + m_deploymentVersionRepository.get().size();
        assert sgo.getCurrentVersion().equals(newVersion);

        // clean up this object ourselves; we cannot rely on cleanUp() in this case.
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.unregister(sgo.getID());
                return null;
            }
        }, false, GatewayObject.TOPIC_REMOVED, StatefulGatewayObject.TOPIC_REMOVED);

        assert m_statefulGatewayRepository.get().size() == 0;
    }

    private void testStrangeNamesInGateways() throws InvalidSyntaxException, IOException {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();

        // add a gateway with a weird name.
        events.add(new LogEvent(":)", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        //fill auditlog; no install data
        m_auditLogStore.put(events);

        //see presence of sgo
        int sgrSizeBefore = m_statefulGatewayRepository.get().size();
        m_statefulGatewayRepository.refresh();
        assert m_statefulGatewayRepository.get().size() == sgrSizeBefore + 1 : "After refresh, we expect " + (sgrSizeBefore + 1) + " gateway based on auditlogdata, but we find " + m_statefulGatewayRepository.get().size();
        StatefulGatewayObject sgo = findStatefulGateway(":)");
        sgo.register();
        assert sgo.getRegistrationState().equals(RegistrationState.Registered) : "After registring our gateway, we assume it to be registered.";
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is " + m_statefulGatewayRepository.get().get(0).getProvisioningState();

    }


    private void testStatefulAuditlog() throws IOException, InvalidSyntaxException {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("myGateway", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        //fill auditlog; no install data
        m_auditLogStore.put(events);

        //see presence of sgo
        assert m_statefulGatewayRepository.get().size() == 0 : "Before audit log refresh, we expect nothing in the stateful repository, but we find " + m_statefulGatewayRepository.get().size();
        m_statefulGatewayRepository.refresh();
        assert m_statefulGatewayRepository.get().size() == 1 : "After refresh, we expect 1 gateway based on auditlogdata, but we find " + m_statefulGatewayRepository.get().size();
        StatefulGatewayObject sgo = m_statefulGatewayRepository.get().get(0);
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle) : "We expect our object's provisioning state to be Idle, but it is " + m_statefulGatewayRepository.get().get(0).getProvisioningState();

        //fill auditlog with complete-data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        events.add(new LogEvent("myGateway", 1, 2, 2, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));
        m_auditLogStore.put(events);
        m_statefulGatewayRepository.refresh();

        assert sgo.getLastInstallVersion().equals("123") : "Our last install version should be 123, but it is " + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.InProgress)  : "We expect our object's provisioning state to be InProgress, but it is " + sgo.getProvisioningState();

        //fill auditlog with install data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        props.put(AuditEvent.KEY_SUCCESS, "false");
        events.add(new LogEvent("myGateway", 1, 3, 3, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));
        m_auditLogStore.put(events);
        m_statefulGatewayRepository.refresh();

        assert sgo.getLastInstallVersion().equals("123") : "Our last install version should be 123, but it is " + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.Failed)  : "We expect our object's provisioning state to be Failed, but it is " + sgo.getProvisioningState();
        assert !sgo.getLastInstallSuccess() : "Our last install was not successful, but according to the sgo it was.";

        sgo.acknowledgeInstallVersion("123");
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle)  : "We expect our object's provisioning state to be Idle, but it is " + sgo.getProvisioningState();

        //add another install event.
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        events.add(new LogEvent("myGateway", 1, 4, 4, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));
        m_auditLogStore.put(events);
        m_statefulGatewayRepository.refresh();

        assert sgo.getLastInstallVersion().equals("124") : "Our last install version should be 124, but it is " + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.InProgress)  : "We expect our object's provisioning state to be InProgress, but it is " + sgo.getProvisioningState();

        //fill auditlog with install data
        events = new ArrayList<LogEvent>();
        props = new Properties();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        props.put(AuditEvent.KEY_SUCCESS, "true");
        events.add(new LogEvent("myGateway", 1, 5, 5, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));
        m_auditLogStore.put(events);
        m_statefulGatewayRepository.refresh();

        assert sgo.getLastInstallVersion().equals("124") : "Our last install version should be 124, but it is " + sgo.getLastInstallVersion();
        assert sgo.getProvisioningState().equals(ProvisioningState.OK)  : "We expect our object's provisioning state to be OK, but it is " + sgo.getProvisioningState();
        assert sgo.getLastInstallSuccess() : "Our last install was successful, but according to the sgo it was not.";

        sgo.acknowledgeInstallVersion("124");
        assert sgo.getProvisioningState().equals(ProvisioningState.Idle)  : "We expect our object's provisioning state to be Idle, but it is " + sgo.getProvisioningState();
    }

    private void testStatefulAuditAndRegister() throws Exception {
        //preregister gateway
        final Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, "myNewGateway3");
        final Map<String, String> tags = new HashMap<String, String>();

        final StatefulGatewayObject sgo1 = runAndWaitForEvent(new Callable<StatefulGatewayObject>() {
            public StatefulGatewayObject call() throws Exception {
                return m_statefulGatewayRepository.preregister(attr, tags);
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_ADDED);

        //do checks
        assert sgo1.isRegistered() : "We just preregistered a gateway, so it should be registered.";

        //add auditlog data
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("myNewGateway3", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        m_statefulGatewayRepository.refresh();

        //do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a gateway does not influence its isRegistered().";
        try {
            sgo1.getGatewayObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's gatewayObject.";
        }

        //add auditlog data for other gateway
        events = new ArrayList<LogEvent>();
        props = new Properties();
        events.add(new LogEvent("myNewGateway4", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_auditLogStore.put(events);
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.refresh();
                return false;
            }
        }, false, StatefulGatewayObject.TOPIC_ADDED);
        final StatefulGatewayObject sgo2 = findStatefulGateway("myNewGateway4");

        //do checks
        assert sgo1.isRegistered() : "Adding auditlog data for a gateway does not influence its isRegistered().";
        try {
            sgo1.getGatewayObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo1's gatewayObject.";
        }
        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getGatewayObject();
            assert false : "We should not be able to get sgo2's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        //remove original gateway
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulGatewayRepository.unregister(sgo1.getID());
                return null;
            }
        }, false, GatewayObject.TOPIC_REMOVED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        //do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be registered.";
        try {
            sgo1.getGatewayObject();
            assert false : "We should not be able to get sgo1's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            //expected
        }
        assert !sgo2.isRegistered() : "sgo2 is only found in the auditlog, so it cannot be in registered.";
        try {
            sgo2.getGatewayObject();
            assert false : "We should not be able to get sgo2's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            // expected
        }

        //register second gateway
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo2.register();
                return null;
            }
        }, false, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        //do checks
        assert !sgo1.isRegistered() : "sgo1 is now only found in the auditlog, so it cannot be in registered.";
        try {
            sgo1.getGatewayObject();
            assert false : "We should not be able to get sgo1's gatewayObject.";
        }
        catch (IllegalStateException ise) {
            //expected
        }
        assert sgo2.isRegistered() : "sgo2 has been registered.";
        try {
            sgo2.getGatewayObject();
        }
        catch (IllegalStateException ise) {
            assert false : "We should be able to get sgo2's gatewayObject.";
        }

        int nrRegistered = m_statefulGatewayRepository.get(m_context.createFilter("(" + StatefulGatewayObject.KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")")).size();
        assert nrRegistered == 1 : "We expect to filter out one registered gateway, but we find " + nrRegistered;

        // Finally, create a license object
        final LicenseObject l1 = createBasicLicenseObject("thelicense");

        assert !sgo1.isRegistered() : "We just created a Staful GW object, is should not be registered";

        // register sgo1 again and create an association in 1 go
        License2GatewayAssociation lgw1 = runAndWaitForEvent(new Callable<License2GatewayAssociation>() {
            public License2GatewayAssociation call() throws Exception {
                sgo1.register();
                return m_license2gatewayRepository.create(l1, sgo1.getGatewayObject());
            }
        }, false, License2GatewayAssociation.TOPIC_ADDED, GatewayObject.TOPIC_ADDED, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        // checks
        nrRegistered = m_statefulGatewayRepository.get(m_context.createFilter("(" + StatefulGatewayObject.KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")")).size();
        assert nrRegistered == 2 : "We expect to filter out two registered gateways, but we find " + nrRegistered;
        assert sgo1.isRegistered() : "A stateful gw object should be registered";
        assert sgo1.isAssociated(l1, LicenseObject.class) : "The stateful gw object should be associated to thelicense.";
        assert lgw1.isSatisfied() : "Both ends of license - stateful gw should be satisfied.";
    }

    private StatefulGatewayObject findStatefulGateway(String gatewayID) throws InvalidSyntaxException {
        for (StatefulGatewayObject sgo : m_statefulGatewayRepository.get()) {
            if (sgo.getID().equals(gatewayID)) {
                return sgo;
            }
        }
        return null;
    }

    @Test( groups = { TestUtils.INTEGRATION } )
    public void testStatefulApprovalWithArtifacts() throws Exception {
        // some setup: we need a helper.
        ArtifactHelper myHelper = new MockArtifactHelper("mymime");

        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Service myHelperService = m_depManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(myHelper);

        m_depManager.add(myHelperService);

        // Empty tag map to be reused througout test
        final Map<String, String> tags = new HashMap<String, String>();

        // First, create a bundle and an artifact, but do not provide a processor for the artifact.
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_URL, "http://myobject");
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, "my.processor.pid");
        attr.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        ArtifactObject a1 = m_artifactRepository.create(attr, tags);

        GroupObject g = createBasicGroupObject("group");
        LicenseObject l = createBasicLicenseObject("license");

        attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, "myGateway");

        StatefulGatewayObject sgo = m_statefulGatewayRepository.preregister(attr, tags);

        m_artifact2groupRepository.create(b1, g);
        m_artifact2groupRepository.create(a1, g);

        m_group2licenseRepository.create(g, l);

        m_license2gatewayRepository.create(l, sgo.getGatewayObject());

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

        assert toDeploy.length == 3 : "We expect to find three artifacts to deploy, but we find: " + toDeploy.length;
        DeploymentArtifact bundle1 = toDeploy[0];
        assert bundle1.getUrl().equals(b1.getURL()) && (bundle1.getKeys().length == 2) : "The first artifact in the list should be bundle1, but it is '" + toDeploy[0].toString() + "'";
        DeploymentArtifact bundle2 = toDeploy[1];
        assert bundle2.getUrl().equals(b2.getURL()) && (bundle2.getKeys().length == 3) && bundle2.getDirective(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER).equals("true"): "The first artifact in the list should be bundle1, but it is '" + toDeploy[0].toString() + "'";
        DeploymentArtifact artifact1 = toDeploy[2];
        assert artifact1.getUrl().equals(a1.getURL()) && (artifact1.getKeys().length == 2) && artifact1.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID).equals("my.processor.pid"): "The first artifact in the list should be bundle1, but it is '" + toDeploy[0].toString() + "'";

        cleanUp();

        m_depManager.remove(myHelperService);
    }


    @Test( groups = { TestUtils.INTEGRATION } )
    public void testAutoGatewayOperator() throws Exception {
        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        // configure automation bundle; new configuration properties; bundle will start
        final Properties props = new Properties();
        props.put("registerGatewayFilter", "(id=anotherGate*)");
        props.put("approveGatewayFilter", "(id=DO_NOTHING)");
        props.put("autoApproveGatewayFilter", "(id=anotherGate*)");
        props.put("commitRepositories","true");
        props.put("gatewayRepository","gateway");
        props.put("deploymentRepository","deployment");
        props.put("storeRepository","store");
        props.put("customerName","apache");
        props.put("hostName",HOST);
        props.put("endpoint",ENDPOINT);

        final Configuration config = m_configAdmin.getConfiguration("org.apache.ace.client.automation", null);

        /*
         * First test the basic scenario where we create some auditlog data, this gateway should be auto-registered after max 1 sec.
         */
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                config.update(props);
                return null;
            }
        }, true, RepositoryAdmin.TOPIC_LOGIN);

        testAutoGatewayReg();

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

    private void testAutoGatewayReg() throws Exception {
        List<LogEvent> events = new ArrayList<LogEvent>();
        Properties props = new Properties();
        events.add(new LogEvent("anotherGateway", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        //fill auditlog; no install data
        m_auditLogStore.put(events);

        int initRepoSize = m_statefulGatewayRepository.get().size();

        // Get the processauditlog task and run it
        ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Runnable.class.getName() + ")(" + SchedulerConstants.SCHEDULER_NAME_KEY + "=" + "org.apache.ace.client.processauditlog" + "))"), null);
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

            assert m_statefulGatewayRepository.get().size() == initRepoSize + 1 : "After refresh, we expect 1 gateway based on auditlogdata, but we find " + m_statefulGatewayRepository.get().size();
            List<StatefulGatewayObject> sgoList = m_statefulGatewayRepository.get(m_context.createFilter("(id=anotherG*)"));
            StatefulGatewayObject sgo = sgoList.get(0);
            assert sgo != null : "Expected one (anotherGateway) in the list.";

            // should be registered and auto approved
            assert sgo.isRegistered(): "The automation gw operator should have registered anotherGateway.";
            assert sgo.getAutoApprove(): "The automation gw operator should have auto-approved anotherGateway.";

            // add a gateway which will not be autoregistered
            events.clear();
            events.add(new LogEvent("secondGateway", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
            m_auditLogStore.put(events);

            // do auto gateway action
            processAuditlog.run();
            assert m_statefulGatewayRepository.get().size() == initRepoSize + 2 : "After refresh, we expect an additional gateway based on auditlogdata, but we find " + m_statefulGatewayRepository.get().size();
            sgoList = m_statefulGatewayRepository.get(m_context.createFilter("(id=second*)"));
            sgo = sgoList.get(0);

            // second gateway should not be registered
            assert !sgo.isRegistered(): "The automation gw operator should not have registered secongGateway.";
            assert !sgo.getAutoApprove(): "The automation gw operator should not have auto-approved myGateway.";
        }
        else
        {
            assert false : "Could not get a reference to the processAuditLog task.";
        }
    }

    @Test( groups = { TestUtils.INTEGRATION } )
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

    @Test( groups = { TestUtils.INTEGRATION } )
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
            //expected
        }

        try {
            m_artifactRepository.importArtifact(temp.toURI().toURL(), "notTheBundleMime", true);
            assert false : "We have given an illegal mimetype, so no recognizer or helper can be found.";
        }
        catch (IllegalArgumentException re) {
            //expected
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
            //expected
        }

        // Supply the OBR.
        addObr("/obr", "store");
        m_artifactRepository.setObrBase(new URL("http://localhost:" + TestConstants.PORT + "/obr/"));

        m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

        assert m_artifactRepository.get().size() == 1;
        assert m_artifactRepository.getResourceProcessors().size() == 0;

        // Create a JAR file which looks like a resource processor supplying bundle.
        attributes.putValue(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "someProcessor");

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

        @SuppressWarnings("unchecked")
        public Dictionary getCredentials() {
            return null;
        }

        public boolean hasCredential(String arg0, Object arg1) {
            return false;
        }

        public String getName() {
            return m_name;
        }

        @SuppressWarnings("unchecked")
        public Dictionary getProperties() {
            return null;
        }

        public int getType() {
            return 0;
        }
    }

    @Test( groups = { TestUtils.INTEGRATION } )
    public void testLoginLogoutAndLoginOnceAgainWhileCreatingAnAssociation() throws IOException, InterruptedException, InvalidSyntaxException {
        User user1 = new MockUser("user1");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext1.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", true);
        m_repositoryAdmin.login(loginContext1);

        GroupObject g1 = createBasicGroupObject("group1");
        LicenseObject l1 = createBasicLicenseObject("license1");

        m_group2licenseRepository.create(g1, l1);

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
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testReadOnlyRepositoryAccess() throws Exception {
        User user1 = new MockUser("user1");

        startRepositoryService();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("gatewayInstance", "apache", "gateway", true);

        final RepositoryAdminLoginContext loginContext1 = m_repositoryAdmin.createLoginContext(user1);
        loginContext1.addShopRepository(new URL(HOST + ENDPOINT), "apache", "store", true);
        loginContext1.addGatewayRepository(new URL(HOST + ENDPOINT), "apache", "gateway", false);
        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        createBasicGroupObject("group1");
        createBasicGatewayObject("gateway1");

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        assert m_groupRepository.get().size() == 1 : "We expect our own group object in the repository; we find " + m_groupRepository.get().size();
        assert m_gatewayRepository.get().size() == 1 : "We expect our own gateway object in the repository; we find " + m_gatewayRepository.get().size();

        m_repositoryAdmin.commit();

        m_repositoryAdmin.logout(false);

        m_repositoryAdmin.login(loginContext1);

        m_repositoryAdmin.checkout();

        assert m_groupRepository.get().size() == 1 : "We expect our own group object in the repository; we find " + m_groupRepository.get().size();
        assert m_gatewayRepository.get().size() == 0 : "Since the gateway repository will not be committed, we expect no gateway objects in the repository; we find " + m_gatewayRepository.get().size();

        cleanUp();
        try {
            removeAllRepositories();
        }
        catch (IOException ioe) {
            // too bad.
        }
    }

    @SuppressWarnings("unchecked")
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testRepostoryLoginDoubleRepository() throws Exception {
        RepositoryAdminLoginContext context = m_repositoryAdmin.createLoginContext(new MockUser("user"));
        context.addRepositories(new URL("http://localhost:" + TestConstants.PORT), "apache", "shop", true, ArtifactRepository.class, Artifact2GroupAssociationRepository.class, GroupRepository.class);
        context.addRepositories(new URL("http://localhost:" + TestConstants.PORT), "apache", "deployment", true, GroupRepository.class, Group2LicenseAssociationRepository.class, LicenseRepository.class);
        try {
            m_repositoryAdmin.login(context);
            assert false : "We tried to log in with two repositories that try to access the same repository service; this should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }
    }

    private static interface newRepository extends ObjectRepository<LicenseObject> {}

    @SuppressWarnings("unchecked")
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testRepostoryLoginRepositoryWithoutImplementation() throws Exception {
        RepositoryAdminLoginContext context = m_repositoryAdmin.createLoginContext(new MockUser("user"));
        context.addRepositories(new URL("http://localhost:" + TestConstants.PORT), "apache", "shop", true, ArtifactRepository.class, Artifact2GroupAssociationRepository.class, GroupRepository.class);
        context.addRepositories(new URL("http://localhost:" + TestConstants.PORT), "apache", "deployment", true, GroupRepository.class, Group2LicenseAssociationRepository.class, newRepository.class);
        try {
            m_repositoryAdmin.login(context);
            assert false : "We tried to log in with a repository for which no implementation is available; this should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }
    }

    /**
     * Tests the template processing mechanism: given a custom processor, do the correct calls go out?
     */
    @SuppressWarnings("unchecked")
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testTemplateProcessingInfrastructure() throws Exception {
        // create a preprocessor
        MockArtifactPreprocessor preprocessor = new MockArtifactPreprocessor();

        // create a helper
        MockArtifactHelper helper = new MockArtifactHelper("mymime", preprocessor);

        // register preprocessor and helper
        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Service helperService = m_depManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(helper);

        m_depManager.add(helperService);

        // create some tree from artifacts to a gateway
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                ArtifactObject b1 = createBasicBundleObject("myBundle");
                ArtifactObject b2 = createBasicBundleObject("myProcessor", "1.0.0", "myProcessor.pid");
                ArtifactObject a1 = createBasicArtifactObject("myArtifact", "mymime", "myProcessor.pid");
                GroupObject go = createBasicGroupObject("mygroup");
                LicenseObject lo = createBasicLicenseObject("mylicense");
                GatewayObject gwo = createBasicGatewayObject("templategateway");
                m_artifact2groupRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_artifact2groupRepository.create(a1, go);
                m_group2licenseRepository.create(go, lo);
                m_license2gatewayRepository.create(lo, gwo);
                return null;
            }
        }, false, StatefulGatewayObject.TOPIC_ADDED);

        StatefulGatewayObject sgo = m_statefulGatewayRepository.get(m_context.createFilter("(" + GatewayObject.KEY_ID + "=templategateway)")).get(0);

        // wait until needsApprove is true; depending on timing, this could have happened before or after the TOPIC_ADDED.
        int attempts = 0;
        while (!sgo.needsApprove() && (attempts < 10)) {
            Thread.sleep(10);
        }
        assert sgo.needsApprove() : "With the new assignments, the SGO should need approval.";
        // create a deploymentversion
        sgo.approve();

//        // the preprocessor now has gotten its properties; inspect these
        PropertyResolver gateway = preprocessor.getProps();
        assert gateway.get("id").equals("templategateway") : "The property resolver should be able to resolve 'id'.";
        assert gateway.get("name").equals("mylicense") : "The property resolver should be able to resolve 'name'.";
        assert gateway.get("someunknownproperty") == null : "The property resolver should not be able to resolve 'someunknownproperty'.";

        cleanUp(); // we need to do this before the helper goes away

        m_depManager.remove(helperService);
    }

    /**
     * Tests the full template mechanism, from importing templatable artifacts, to creating deployment
     * versions with it. It uses the configuration (autoconf) helper, which uses a VelocityBased preprocessor.
     */
    @SuppressWarnings("unchecked")
    @Test( groups = { TestUtils.INTEGRATION } )
    public void testTemplateProcessing() throws Exception {
        addObr("/obr", "store");
        m_artifactRepository.setObrBase(new URL("http://localhost:" + TestConstants.PORT + "/obr/"));

        // create some template things
        String xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<metatype:MetaData xmlns:metatype= \"http://www.osgi.org/xmlns/metatype/v1.0.0\">\n";
        String xmlFooter = "\n</metatype:MetaData>";

        String noTemplate = "<Attribute content=\"http://someURL\"/>";
        String noTemplateProcessed = "<Attribute content=\"http://someURL\"/>";
        final File noTemplateFile = createFileWithContents("template", "xml", xmlHeader+noTemplate+xmlFooter);

        String simpleTemplate = "<Attribute content=\"http://$context.name\"/>";
        String simpleTemplateProcessed = "<Attribute content=\"http://mylicense\"/>";
        File simpleTemplateFile = createFileWithContents("template", "xml", xmlHeader+simpleTemplate+xmlFooter);

        // create some tree from artifacts to a gateway
        GroupObject go = runAndWaitForEvent(new Callable<GroupObject>() {
            public GroupObject call() throws Exception {
                ArtifactObject b1 = createBasicBundleObject("myBundle");
                ArtifactObject b2 = createBasicBundleObject("myProcessor", "1.0.0", "org.osgi.deployment.rp.autoconf");
                GroupObject go = createBasicGroupObject("mygroup");
                LicenseObject lo = createBasicLicenseObject("mylicense");
                GatewayObject gwo = createBasicGatewayObject("templategateway2");
                m_artifact2groupRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_group2licenseRepository.create(go, lo);
                m_license2gatewayRepository.create(lo, gwo);
                return go;
            }
        }, false, StatefulGatewayObject.TOPIC_ADDED);

        ArtifactObject a1 = m_artifactRepository.importArtifact(noTemplateFile.toURI().toURL(), true);
        Artifact2GroupAssociation a2g = m_artifact2groupRepository.create(a1, go);

        final StatefulGatewayObject sgo = m_statefulGatewayRepository.get(m_context.createFilter("(" + GatewayObject.KEY_ID + "=templategateway2)")).get(0);

        // create a deploymentversion
        assert sgo.needsApprove() : "With the new assignments, the SGO should need approval.";
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                sgo.approve();
                return null;
            }
        }, false, StatefulGatewayObject.TOPIC_STATUS_CHANGED);

        // find the deployment version
        DeploymentVersionObject dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templategateway2");
        String inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assert inFile.equals(xmlHeader + noTemplateProcessed + xmlFooter) : "We expected to find\n" + xmlHeader + noTemplateProcessed + xmlFooter + "\n in the processed artifact, but found\n" + inFile;

        // try the simple template
        m_artifact2groupRepository.remove(a2g);
        a1 = m_artifactRepository.importArtifact(simpleTemplateFile.toURI().toURL(), true);
        a2g = m_artifact2groupRepository.create(a1, go);

        sgo.approve();

        // find the deployment version
        dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templategateway2");
        // sleep for a while, to allow the OBR to process the file.
        Thread.sleep(1000);

        inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assert inFile.equals(xmlHeader + simpleTemplateProcessed + xmlFooter) : "We expected to find\n" + xmlHeader + simpleTemplateProcessed + xmlFooter + "\n in the processed artifact, but found\n" + inFile;

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
        if (m_runAndWaitDebug) {System.err.println("Received event: " + event.getTopic());}
        if (m_waitingForTopic.remove(event.getTopic())) {
            if (m_runAndWaitDebug) {System.err.println("Event was expected.");}
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

    private ArtifactObject createBasicArtifactObject(String name, String mimetype, String processorPID) throws InterruptedException {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        attr.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        attr.put(ArtifactObject.KEY_URL, "http://" + name);
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, processorPID);
        Map<String, String> tags = new HashMap<String, String>();

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

    private DeploymentVersionObject createBasicDeploymentVersionObject(String gatewayID, String version, ArtifactObject... bundles) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DeploymentVersionObject.KEY_GATEWAYID, gatewayID);
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

    private Artifact2GroupAssociation createDynamicBundle2GroupAssociation(ArtifactObject artifact, GroupObject group) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
        return m_artifact2groupRepository.create(artifact, properties, group, null);
    }

    /*
     * The following code is borrowed from RepositoryTest.java, and is used to instantiate and
     * use repository servlets.
     */

    protected void startRepositoryService() throws IOException {
        // configure the (replication)repository servlets
        setProperty("org.apache.ace.repository.servlet.RepositoryServlet", new Object[][] { { HttpConstants.ENDPOINT, ENDPOINT } });
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        // remove all repositories, in case a test case does not reach it's cleanup section due to an exception
        removeAllRepositories();
    }

    /* Configure a new repository instance */
    private void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException, InterruptedException, InvalidSyntaxException {
        // Publish configuration for a repository instance
        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, customer);
        props.put(RepositoryConstants.REPOSITORY_NAME, name);
        props.put(RepositoryConstants.REPOSITORY_MASTER, String.valueOf(isMaster));
        props.put("factory.instance.pid", instanceName);
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
        tracker.open();

        config.update(props);

        if (tracker.waitForService(5000) == null) {
            throw new IOException("Did not get notified about new repository becoming available in time.");
        }
        tracker.close();
    }

    private void addObr(String endpoint, String fileLocation) throws IOException, InterruptedException, InvalidSyntaxException {
        Properties propsServlet = new Properties();
        propsServlet.put(HttpConstants.ENDPOINT, endpoint);
        propsServlet.put("OBRInstance", "singleOBRServlet");
        Properties propsStore = new Properties();
        propsStore.put(OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);
        propsStore.put("OBRInstance", "singleOBRStore");

        Configuration configServlet = m_configAdmin.getConfiguration("org.apache.ace.obr.servlet", null);
        Configuration configStore = m_configAdmin.getConfiguration("org.apache.ace.obr.storage.file", null);

        configServlet.update(propsServlet);
        configStore.update(propsStore);

        // Wait for the endpoint to respond.
        // TODO below there is a similar url that does put a slash between port and endpoint, why?
        URL url = new URL("http://localhost:" + TestConstants.PORT + endpoint + "/repository.xml");
        int response = ((HttpURLConnection) url.openConnection()).getResponseCode();
        int tries = 0;
        while ((response != 200) && (tries < 50)) {
            Thread.sleep(100); //If we get interrupted, there will be a good reason for it.
            response = ((HttpURLConnection) url.openConnection()).getResponseCode();
            tries++;
        }
        if (tries == 50) {
            throw new IOException("The OBR servlet does not seem to be responding well. Last response code: " + response);
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
            Thread.sleep(100); //If we get interrupted, there will be a good reason for it.
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

            ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(" + Constants.OBJECTCLASS + "=" + Repository.class.getName() + ")"), null) {
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

    /* Configure properties for the specified service PID */
    @SuppressWarnings("unchecked")
    private void setProperty(String pid, Object[][] props) throws IOException {
        Configuration configuration = m_configAdmin.getConfiguration(pid, null);
        Dictionary dictionary = configuration.getProperties();
        if (dictionary == null) {
            dictionary = new Hashtable();
        }
        for (Object[] pair : props) {
            dictionary.put(pair[0], pair[1]);
        }
        configuration.update(dictionary);
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
        return new String[0];
    }

    public String[] getMandatoryAttributes() {
        return new String[0];
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
    public String preprocess(String url, PropertyResolver props, String gatewayID, String version, URL obrBase) throws IOException {
        m_props = props;
        return url;
    }

    PropertyResolver getProps() {
        return m_props;
    }

    public boolean needsNewVersion(String url, PropertyResolver props, String gatewayID, String fromVersion) {
        return false;
    }
}
