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

import static org.apache.ace.client.repository.stateful.StatefulTargetObject.KEY_ID;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.KEY_REGISTRATION_STATE;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ADDED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_REMOVED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_STATUS_CHANGED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.UNKNOWN_VERSION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.ProvisioningState;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.RegistrationState;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.StoreState;
import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Event;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.User;

/**
 * Provides test cases for the stateful target repository.
 */
public class StatefulTargetRepositoryTest extends BaseRepositoryAdminTest {

    public void testStatefulApprove() throws Exception {
        setUpTestCase();

        final String targetId = "myNewTarget2";

        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, targetId);

        final Map<String, String> tags = new HashMap<>();
        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assertTrue("Without any deployment versions, and no information in the shop, we should not need to approve.", !sgo.needsApprove());
        assertEquals("We expect the registration state to be Registered;", RegistrationState.Registered, sgo.getRegistrationState());
        assertEquals("We expect the registration state to be New;", StoreState.New, sgo.getStoreState());
        assertEquals(UNKNOWN_VERSION, sgo.getCurrentVersion());

        final ArtifactObject b11 = createBasicBundleObject("bundle1", "1", null);

        FeatureObject g1 = createBasicFeatureObject("feature1");
        FeatureObject g2 = createBasicFeatureObject("feature2"); // note that this feature is not associated to a
                                                                 // bundle.

        createDynamicBundle2FeatureAssociation(b11, g1);

        final DistributionObject l1 = createBasicDistributionObject("distribution1");

        m_feature2distributionRepository.create(g1, l1);
        m_feature2distributionRepository.create(g2, l1);

        runAndWaitForEvent(new Callable<Distribution2TargetAssociation>() {
            public Distribution2TargetAssociation call() throws Exception {
                return m_distribution2targetRepository.create(l1, sgo.getTargetObject());
            }
        }, false, Distribution2TargetAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertTrue("We added information that influences our target, so we should need to approve it.", sgo.needsApprove());
        assertEquals("We expect the registration state to be Registered;", RegistrationState.Registered, sgo.getRegistrationState());
        assertEquals("We expect the registration state to be Unapproved;", StoreState.Unapproved, sgo.getStoreState());
        assertEquals("According to the shop, this target needs 1 bundle", 1, sgo.getArtifactsFromShop().length);
        assertEquals("According to the deployment, this target needs 0 bundles", 0, sgo.getArtifactsFromDeployment().length);
        assertEquals(UNKNOWN_VERSION, sgo.getCurrentVersion());

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                createBasicDeploymentVersionObject(targetId, "1", b11);
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertFalse("We manually created a deployment version that reflects the shop, so no approval should be necessary.", sgo.needsApprove());
        assertEquals("We expect the registration state to be Registered;", RegistrationState.Registered, sgo.getRegistrationState());
        assertEquals("We expect the registration state to be Approved;", StoreState.Approved, sgo.getStoreState());
        assertEquals("According to the shop, this target needs 1 bundle;", 1, sgo.getArtifactsFromShop().length);
        assertEquals("According to the deployment, this target needs 1 bundle;", 1, sgo.getArtifactsFromDeployment().length);

        runAndWaitForEvent(new Callable<ArtifactObject>() {
            public ArtifactObject call() throws Exception {
                return createBasicBundleObject("bundle1", "2", null);
            }
        }, false, ArtifactObject.TOPIC_ADDED, Artifact2FeatureAssociation.TOPIC_CHANGED, TOPIC_STATUS_CHANGED);

        assertTrue("We added a new version of a bundle that is used by the target, so approval should be necessary.", sgo.needsApprove());
        assertEquals("We expect the registration state to be Registered;", RegistrationState.Registered, sgo.getRegistrationState());
        assertEquals("We expect the registration state to be Unapproved;", StoreState.Unapproved, sgo.getStoreState());
        assertEquals("According to the shop, this target needs 1 bundle", 1, sgo.getArtifactsFromShop().length);
        assertEquals("The shop should tell use we need bundle URL 'bundle1-2';", "http://bundle1-2", sgo.getArtifactsFromShop()[0].getURL());
        assertEquals("According to the deployment, this target needs 1 bundle", 1, sgo.getArtifactsFromDeployment().length);
        assertEquals("The deployment should tell use we need bundle URL 'bundle1-1';", "http://bundle1-1", sgo.getArtifactsFromDeployment()[0].getUrl());
        assertEquals("1", sgo.getCurrentVersion());

        String newVersion = sgo.approve();

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertFalse("Immediately after approval, no approval is necessary.", sgo.needsApprove());
        assertEquals("We expect the registration state to be Registered;", RegistrationState.Registered, sgo.getRegistrationState());
        assertEquals("We expect the registration state to be Approved;", StoreState.Approved, sgo.getStoreState());
        assertEquals("According to the shop, this target needs 1 bundle", 1, sgo.getArtifactsFromShop().length);
        assertEquals("The shop should tell use we need bundle URL 'bundle1-2';", "http://bundle1-2", sgo.getArtifactsFromShop()[0].getURL());
        assertEquals("According to the deployment, this target needs 1 bundle", 1, sgo.getArtifactsFromDeployment().length);
        assertEquals("The deployment should tell use we need bundle URL 'bundle1-2';", "http://bundle1-2", sgo.getArtifactsFromDeployment()[0].getUrl());
        assertEquals("We expect two deployment versions;", 2, m_deploymentVersionRepository.get().size());
        assertEquals(newVersion, sgo.getCurrentVersion());

        // clean up this object ourselves; we cannot rely on cleanUp() in this case.
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(targetId);
                return null;
            }
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);

        StatefulTargetObject target = findStatefulTarget(targetId);
        assertNull("Target should be removed?!", target);

        cleanUp();
    }

    public void testStatefulAuditAndRegister() throws Exception {
        setUpTestCase();

        // preregister target
        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, "myNewTarget3");
        final Map<String, String> tags = new HashMap<>();

        final StatefulTargetObject sgo1 = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        // do checks
        assertTrue("We just preregistered a target, so it should be registered.", sgo1.isRegistered());

        // add auditlog data
        List<Event> events = new ArrayList<>();
        events.add(new Event("myNewTarget3", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        // add an (old) set of target properties
        Map<String, String> props2 = new HashMap<>();
        props2.put("mykey", "myoldvalue");
        props2.put("myoldkey", "myoldvalue");
        events.add(new Event("myNewTarget3", 1, 2, 2, AuditEvent.TARGETPROPERTIES_SET, props2));
        // add a new set of target properties
        Map<String, String> props3 = new HashMap<>();
        props3.put("mykey", "myvalue");
        events.add(new Event("myNewTarget3", 1, 3, 3, AuditEvent.TARGETPROPERTIES_SET, props3));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        // do checks
        TargetObject to = sgo1.getTargetObject();
        assertNotNull("Stateful target should be backed by a target object.", to);
        assertEquals("Target should have a property mykey with value myvalue.", "myvalue", to.getTag("target.mykey"));
        assertNull("This old key should no longer have been associated with this target.", to.getTag("target.myoldkey"));
        assertTrue("Adding auditlog data for a target does not influence its isRegistered().", sgo1.isRegistered());

        // add auditlog data for other target
        events = new ArrayList<>();
        events.add(new Event("myNewTarget4", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        m_auditLogStore.put(events);
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.refresh();
                return false;
            }
        }, false, TOPIC_ADDED);
        final StatefulTargetObject sgo2 = findStatefulTarget("myNewTarget4");

        // do checks
        assertTrue("Adding auditlog data for a target does not influence its isRegistered().", sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assertTrue("We should be able to get sgo1's targetObject.", false);
        }
        assertTrue("sgo2 is only found in the auditlog, so it cannot be in registered.", !sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
            assertTrue("We should not be able to get sgo2's targetObject.", false);
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
        assertTrue("sgo1 is now only found in the auditlog, so it cannot be registered.", !sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
            assertTrue("We should not be able to get sgo1's targetObject.", false);
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assertTrue("sgo2 is only found in the auditlog, so it cannot be in registered.", !sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
            assertTrue("We should not be able to get sgo2's targetObject.", false);
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
        assertTrue("sgo1 is now only found in the auditlog, so it cannot be in registered.", !sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
            assertTrue("We should not be able to get sgo1's targetObject.", false);
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assertTrue("sgo2 has been registered.", sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assertTrue("We should be able to get sgo2's targetObject.", false);
        }

        int nrRegistered =
            m_statefulTargetRepository.get(
                m_bundleContext.createFilter("(" + KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")"))
                .size();
        assert nrRegistered == 1 : "We expect to filter out one registered target, but we find " + nrRegistered;

        // Finally, create a distribution object
        final DistributionObject l1 = createBasicDistributionObject("thedistribution");

        assertTrue("We just created a Stateful target object, is should not be registered", !sgo1.isRegistered());

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
        assertTrue("A stateful target object should be registered", sgo1.isRegistered());
        assertTrue("The stateful target object should be associated to the distribution.", sgo1.isAssociated(l1, DistributionObject.class));
        assertTrue("Both ends of distribution - stateful target should be satisfied.", lgw1.isSatisfied());

        cleanUp();
    }

    public void testStatefulAuditAndRemove() throws Exception {
        setUpTestCase();

        // preregister gateway
        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, "myNewGatewayA");
        final Map<String, String> tags = new HashMap<>();

        final StatefulTargetObject sgo1 = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        // do checks
        assertTrue("We just preregistered a gateway, so it should be registered.", sgo1.isRegistered());

        // add auditlog data
        List<Event> events = new ArrayList<>();
        events.add(new Event("myNewGatewayA", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        m_auditLogStore.put(events);
        m_statefulTargetRepository.refresh();

        // do checks
        assertTrue("Adding auditlog data for a gateway does not influence its isRegistered().", sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assertTrue("We should be able to get sgo1's gatewayObject.", false);
        }
        // add auditlog data for other gateway
        events = new ArrayList<>();
        events.add(new Event("myNewGatewayB", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        m_auditLogStore.put(events);
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.refresh();
                return false;
            }
        }, false, TOPIC_ADDED);
        final StatefulTargetObject sgo2 = findStatefulTarget("myNewGatewayB");

        // do checks
        assertTrue("Adding auditlog data for a gateway does not influence its isRegistered().", sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assertTrue("We should be able to get sgo1's gatewayObject.", false);
        }
        assertTrue("sgo2 is only found in the auditlog, so it cannot be in registered.", !sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
            assertTrue("We should not be able to get sgo2's gatewayObject.", false);
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
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);

        // do checks
        assertTrue("sgo1 is now only found in the auditlog, so it cannot be registered.", !sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
            assertTrue("We should not be able to get sgo1's gatewayObject.", false);
        }
        catch (IllegalStateException ise) {
            // expected
        }

        assertTrue("sgo2 is only found in the auditlog, so it cannot be in registered.", !sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
            assertTrue("We should not be able to get sgo2's gatewayObject.", false);
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
        assertTrue("sgo1 is now only found in the auditlog, so it cannot be in registered.", !sgo1.isRegistered());
        try {
            sgo1.getTargetObject();
            assertTrue("We should not be able to get sgo1's gatewayObject.", false);
        }
        catch (IllegalStateException ise) {
            // expected
        }
        assertTrue("sgo2 has been registered.", sgo2.isRegistered());
        try {
            sgo2.getTargetObject();
        }
        catch (IllegalStateException ise) {
            assertTrue("We should be able to get sgo2's gatewayObject.", false);
        }

        int nrRegistered = m_statefulTargetRepository.get(m_bundleContext.createFilter("(" + KEY_REGISTRATION_STATE + "=" + RegistrationState.Registered + ")")).size();
        assert nrRegistered == 1 : "We expect to filter out one registered gateway, but we find " + nrRegistered;

        // Finally, refresh the repository; it should cause sgo1 to be re-created (due to its audit log)...
        // ACE-167 does not cover this scenario, but at a later time this should be fixed as well (see ACE-230).
        m_statefulTargetRepository.refresh();

        int count = m_statefulTargetRepository.get(m_bundleContext.createFilter("(" + KEY_ID + "=myNewGatewayA)")).size();
        assertTrue("We expected sgo1 to be re-created!", count == 1);

        cleanUp();
    }

    public void testStatefulAuditLog() throws Exception {
        setUpTestCase();

        final String targetId = String.format("target-%s", Long.toHexString(System.nanoTime()));

        List<Event> events = new ArrayList<>();

        events.add(new Event(targetId, 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED, StatefulTargetObject.TOPIC_ADDED);

        StatefulTargetObject sgo = findStatefulTarget(targetId);
        assertNotNull("Expected new target object to become available!", sgo);

        assertEquals("We expect our object's provisioning state to be Idle;", ProvisioningState.Idle, sgo.getProvisioningState());

        // fill auditlog with complete-data
        events = new ArrayList<>();
        Map<String, String> props = new HashMap<>();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        events.add(new Event(targetId, 1, 2, 2, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));

        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_STATUS_CHANGED, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED);

        assertEquals("Our last install version should be 123;", "123", sgo.getLastInstallVersion());
        assertEquals("We expect our object's provisioning state to be InProgress;", ProvisioningState.InProgress, sgo.getProvisioningState());

        // fill auditlog with install data
        events = new ArrayList<>();
        props = new HashMap<>();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "123");
        props.put(AuditEvent.KEY_SUCCESS, "false");
        events.add(new Event(targetId, 1, 3, 3, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));

        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED, StatefulTargetObject.TOPIC_STATUS_CHANGED);

        assertEquals("Our last install version should be 123;", "123", sgo.getLastInstallVersion());
        assertEquals("We expect our object's provisioning state to be Failed;", ProvisioningState.Failed, sgo.getProvisioningState());
        assertFalse("Our last install was not successful, but according to the sgo it was.", sgo.getLastInstallSuccess());

        sgo.acknowledgeInstallVersion("123");

        assertEquals("We expect our object's provisioning state to be Idle;", ProvisioningState.Idle, sgo.getProvisioningState());

        // add another install event.
        events = new ArrayList<>();
        props = new HashMap<>();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        events.add(new Event(targetId, 1, 4, 4, AuditEvent.DEPLOYMENTCONTROL_INSTALL, props));

        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED, StatefulTargetObject.TOPIC_STATUS_CHANGED);

        assertEquals("Our last install version should be 124;", "124", sgo.getLastInstallVersion());
        assertEquals("We expect our object's provisioning state to be InProgress;", ProvisioningState.InProgress, sgo.getProvisioningState());

        // fill auditlog with install data
        events = new ArrayList<>();
        props = new HashMap<>();
        props.put(AuditEvent.KEY_NAME, "mypackage");
        props.put(AuditEvent.KEY_VERSION, "124");
        props.put(AuditEvent.KEY_SUCCESS, "true");
        events.add(new Event(targetId, 1, 5, 5, AuditEvent.DEPLOYMENTADMIN_COMPLETE, props));

        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED, StatefulTargetObject.TOPIC_STATUS_CHANGED);

        assertEquals("Our last install version should be 124;", "124", sgo.getLastInstallVersion());
        assertEquals("We expect our object's provisioning state to be OK;", ProvisioningState.OK, sgo.getProvisioningState());
        assertTrue("Our last install was successful, but according to the sgo it was not.", sgo.getLastInstallSuccess());

        sgo.acknowledgeInstallVersion("124");

        assertEquals("We expect our object's provisioning state to be Idle;", ProvisioningState.Idle, sgo.getProvisioningState());
    }

    public void testStatefulCreateRemove() throws Exception {
        setUpTestCase();

        final String targetId = "myNewTarget1";

        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, targetId);
        final Map<String, String> tags = new HashMap<>();

        try {
            m_statefulTargetRepository.create(attr, tags);
            fail("Creating a stateful target repository should not be allowed.");
        }
        catch (UnsupportedOperationException uoe) {
            // expected
        }

        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assertNotNull("We expect to found our new target in the repository!", findStatefulTarget(targetId));

        // Removing stateful objects is now (partially) supported; see ACE-167 & ACE-230...
        m_statefulTargetRepository.remove(sgo);

        assertNull("We expect to NOT found our target in the repository!", findStatefulTarget(targetId));

        cleanUp();
    }

    public void testStatefulSetAutoApprove() throws Exception {
        setUpTestCase();

        final String targetId = "a_target";

        // register target with
        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, targetId);
        attr.put(TargetObject.KEY_AUTO_APPROVE, String.valueOf(true));
        final Map<String, String> tags = new HashMap<>();

        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assertNotNull("We expect to found our new target in the repository!", findStatefulTarget(targetId));

        assertTrue("The target should have auto approved value: true but got: false.", sgo.getAutoApprove());

        sgo.setAutoApprove(false);

        assertFalse("The target should have auto approved value: false but got: true.", sgo.getAutoApprove());

        // clean up
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_statefulTargetRepository.unregister(targetId);
                return null;
            }
        }, false, TargetObject.TOPIC_REMOVED, TOPIC_REMOVED);
    }

    public void testStrangeNamesInTargets() throws Exception {
        setUpTestCase();

        final String targetID = ":)";

        List<Event> events = new ArrayList<>();

        // add a target with a weird name.
        events.add(new Event(targetID, 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_statefulTargetRepository.refresh();
                return null;
            }
        }, false, StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED, StatefulTargetObject.TOPIC_ADDED);

        StatefulTargetObject sgo = findStatefulTarget(targetID);
        assertNotNull("Target not added to repository?!", sgo);

        sgo.register();

        assertTrue("After registring our target, we assume it to be registered.", sgo.getRegistrationState().equals(RegistrationState.Registered));

        assertEquals("We expect our object's provisioning state to be Idle;", ProvisioningState.Idle, sgo.getProvisioningState());
    }

    /**
     * Tests that the artifact sizes are propagated to the {@link DeploymentArtifact}s in the deployment repository, see
     * ACE-384.
     */
    public void testDeploymentVersionObjectSize() throws Exception {
        setUpTestCase();

        final String targetId = "myNewTarget3";

        final Map<String, String> attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, targetId);

        final Map<String, String> tags = new HashMap<>();
        final StatefulTargetObject sgo = runAndWaitForEvent(new Callable<StatefulTargetObject>() {
            public StatefulTargetObject call() throws Exception {
                return m_statefulTargetRepository.preregister(attr, tags);
            }
        }, false, TargetObject.TOPIC_ADDED, TOPIC_ADDED);

        assertEquals(UNKNOWN_VERSION, sgo.getCurrentVersion());

        final ArtifactObject b1 = createBasicBundleObject("bundle1", "1", null, "10");
        final ArtifactObject b2 = createBasicBundleObject("bundle2", "1", null);
        final ArtifactObject b3 = createBasicBundleObject("bundle3", "1", null, "foo");

        FeatureObject f1 = createBasicFeatureObject("feature1");

        createDynamicBundle2FeatureAssociation(b1, f1);
        createDynamicBundle2FeatureAssociation(b2, f1);
        createDynamicBundle2FeatureAssociation(b3, f1);

        final DistributionObject d1 = createBasicDistributionObject("distribution1");

        m_feature2distributionRepository.create(f1, d1);

        runAndWaitForEvent(new Callable<Distribution2TargetAssociation>() {
            public Distribution2TargetAssociation call() throws Exception {
                return m_distribution2targetRepository.create(d1, sgo.getTargetObject());
            }
        }, false, Distribution2TargetAssociation.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertEquals(UNKNOWN_VERSION, sgo.getCurrentVersion());

        DeploymentVersionObject deploymentVersionObject = runAndWaitForEvent(new Callable<DeploymentVersionObject>() {
            public DeploymentVersionObject call() throws Exception {
                return createBasicDeploymentVersionObject(targetId, "1", b1, b2, b3);
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        assertNotNull(deploymentVersionObject);

        DeploymentArtifact[] deploymentArtifacts = deploymentVersionObject.getDeploymentArtifacts();
        for (DeploymentArtifact deploymentArtifact : deploymentArtifacts) {
            if (deploymentArtifact.getUrl().contains("bundle1")) {
                assertEquals(10L, deploymentArtifact.getSize());
            }
            else if (deploymentArtifact.getUrl().contains("bundle2")) {
                assertEquals(-1L, deploymentArtifact.getSize());
            }
            else if (deploymentArtifact.getUrl().contains("bundle3")) {
                assertEquals(-1L, deploymentArtifact.getSize());
            }
            else {
                fail("Unknown bundle?! " + deploymentArtifact.getUrl());
            }
        }
    }

    private DeploymentVersionObject createBasicDeploymentVersionObject(String targetID, String version,
        ArtifactObject... bundles) {
        Map<String, String> attr = new HashMap<>();
        attr.put(DeploymentVersionObject.KEY_TARGETID, targetID);
        attr.put(DeploymentVersionObject.KEY_VERSION, version);
        Map<String, String> tags = new HashMap<>();

        List<DeploymentArtifact> artifacts = new ArrayList<>();
        for (ArtifactObject artifact : bundles) {
            Map<String, String> directives = new HashMap<>();
            directives.put(BundleHelper.KEY_SYMBOLICNAME, artifact.getAttribute(BundleHelper.KEY_SYMBOLICNAME));
            directives.put(DeploymentArtifact.DIRECTIVE_KEY_BASEURL, artifact.getURL());
            if (artifact.getAttribute(BundleHelper.KEY_VERSION) != null) {
                directives.put(BundleHelper.KEY_VERSION, artifact.getAttribute(BundleHelper.KEY_VERSION));
            }
            artifacts.add(m_deploymentVersionRepository.createDeploymentArtifact(artifact.getURL(), artifact.getSize(), directives));
        }
        return m_deploymentVersionRepository.create(attr, tags, artifacts.toArray(new DeploymentArtifact[0]));
    }

    private Artifact2FeatureAssociation createDynamicBundle2FeatureAssociation(ArtifactObject artifact,
        FeatureObject feature) {
        Map<String, String> properties = new HashMap<>();
        properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
        return m_artifact2featureRepository.create(artifact, properties, feature, null);
    }

    /**
     * The following code is borrowed from RepositoryTest.java, and is used to instantiate and use repository servlets.
     */
    private StatefulTargetObject findStatefulTarget(String targetID) throws InvalidSyntaxException {
        for (StatefulTargetObject sgo : m_statefulTargetRepository.get()) {
            if (sgo.getID().equals(targetID)) {
                return sgo;
            }
        }
        return null;
    }

    /**
     * @throws IOException
     * @throws InterruptedException
     * @throws InvalidSyntaxException
     */
    private void setUpTestCase() throws Exception {
        User user = new MockUser();
        String customer = "customer-" + Long.toHexString(System.currentTimeMillis());

        addRepository("storeInstance", customer, "store", true);
        addRepository("targetInstance", customer, "target", true);
        addRepository("deploymentInstance", customer, "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext
            .add(loginContext.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer(customer).setName("store").setWriteable())
            .add(loginContext.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer(customer).setName("target").setWriteable())
            .add(loginContext.createDeploymentRepositoryContext()
                .setLocation(m_endpoint).setCustomer(customer).setName("deployment").setWriteable());

        m_repositoryAdmin.login(loginContext);
        m_repositoryAdmin.checkout();
    }
}
