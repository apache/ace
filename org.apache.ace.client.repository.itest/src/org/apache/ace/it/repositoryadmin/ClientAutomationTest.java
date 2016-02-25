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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Callable;

import org.amdatu.scheduling.Job;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Event;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Provides test cases for the client automation functionality.
 */
public class ClientAutomationTest extends BaseRepositoryAdminTest {

    /*
     * The auto target operator is not yet session-aware; therefore, this test will fail. We should decide what
     * to do with this operator.
     */
    public void testAutoTargetOperator() throws Exception {
        createTestUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        // configure automation bundle; new configuration properties; bundle will start
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("registerTargetFilter", "(id=anotherTarget*)");
        props.put("approveTargetFilter", "(id=DO_NOTHING)");
        props.put("autoApproveTargetFilter", "(id=anotherTarget*)");
        props.put("commitRepositories", "true");
        props.put("targetRepository", "target");
        props.put("deploymentRepository", "deployment");
        props.put("storeRepository", "store");
        props.put("customerName", "apache");
        props.put("hostName", HOST);
        props.put("userName", TEST_USER_NAME);
        props.put("endpoint", ENDPOINT_NAME);

        final Configuration config = m_configAdmin.getConfiguration("org.apache.ace.client.automation", null);

        /*
         * First test the basic scenario where we create some auditlog data, this target should be auto-registered after max 1 sec.
         */
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                config.update(props);
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGIN);

        doAutoTargetReg();

        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                config.delete();
                return null;
            }
        }, false, RepositoryAdmin.TOPIC_LOGOUT);
    }

    private void doAutoTargetReg() throws Exception {
        List<Event> events = new ArrayList<>();
        events.add(new Event("anotherTarget", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
        // fill auditlog; no install data
        m_auditLogStore.put(events);

        int initRepoSize = m_statefulTargetRepository.get().size();

        // Get the processauditlog task and run it
        ServiceTracker<Job, Job> tracker = new ServiceTracker<>(
            m_bundleContext, m_bundleContext.createFilter("(&(" + Constants.OBJECTCLASS + "="
                + Job.class.getName() + ")(name="
                + "org.apache.ace.client.processauditlog" + "))"), null);
        tracker.open();

        final Job processAuditlog = tracker.waitForService(2000);
        if (processAuditlog != null) {
            // commit should be called
            runAndWaitForEvent(new Callable<Object>() {
                public Object call() throws Exception {
                    processAuditlog.execute();
                    return null;
                }
            }, false, RepositoryAdmin.TOPIC_REFRESH);

            assertEquals("After refresh, we expect 1 target based on auditlogdata;", initRepoSize + 1, m_statefulTargetRepository.get().size());
            
            List<StatefulTargetObject> sgoList = m_statefulTargetRepository.get(m_bundleContext.createFilter("(id=another*)"));
            StatefulTargetObject sgo = sgoList.get(0);
            assertTrue("Expected one (anotherTarget) in the list.", sgo != null);

            // should be registered and auto approved
            assertTrue("The automation target operator should have registered anotherTarget.", sgo.isRegistered());
            assertTrue("The automation target operator should have auto-approved anotherTarget.", sgo.getAutoApprove());

            // add a target which will not be autoregistered
            events.clear();
            events.add(new Event("secondTarget", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED));
            m_auditLogStore.put(events);

            // do auto target action
            processAuditlog.execute();
            assertEquals("After refresh, we expect an additional target based on auditlogdata;", initRepoSize + 2, m_statefulTargetRepository.get().size());

            sgoList = m_statefulTargetRepository.get(m_bundleContext.createFilter("(id=second*)"));
            sgo = sgoList.get(0);

            // second target should not be registered
            assertFalse("The automation target operator should not have registered secondTarget.", sgo.isRegistered());
            assertFalse("The automation target operator should not have auto-approved myTarget.", sgo.getAutoApprove());
        }
        else
        {
            assertTrue("Could not get a reference to the processAuditLog task.", false);
        }
    }}
