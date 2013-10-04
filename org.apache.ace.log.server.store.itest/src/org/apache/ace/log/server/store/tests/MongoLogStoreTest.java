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
package org.apache.ace.log.server.store.tests;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.amdatu.mongo.MongoDBService;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.dm.Component;
import org.osgi.service.log.LogService;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;

public class MongoLogStoreTest extends IntegrationTestBase {
    private volatile LogStore m_logStore;
    private volatile MongoDBService m_mongodbService;

    private DBCollection m_collection;

    public void testGetDescriptorsForTarget() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        Map<String, String> props = new HashMap<String, String>();
        props.put("myProperty", "myvalue");

        Event event1 = new Event("mytarget1", 2, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event2 = new Event("mytarget1", 2, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);

        m_logStore.put(Arrays.asList(event1, event2));

        List<Descriptor> descriptors = m_logStore.getDescriptors("mytarget1");
        assertEquals(2, descriptors.size());
        assertEquals("mytarget1", descriptors.get(0).getTargetID());
        assertEquals(1, descriptors.get(0).getStoreID());
        assertEquals(4, descriptors.get(0).getRangeSet().getHigh());

        assertEquals("mytarget1", descriptors.get(1).getTargetID());
        assertEquals(2, descriptors.get(1).getStoreID());
        assertEquals(2, descriptors.get(1).getRangeSet().getHigh());
    }

    public void testGetDescriptorsMultipleLogIds() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        Map<String, String> props = new HashMap<String, String>();
        props.put("myProperty", "myvalue");

        Event event1 = new Event("mytarget1", 2, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event2 = new Event("mytarget1", 2, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);

        m_logStore.put(Arrays.asList(event1, event2));

        List<Descriptor> descriptors = m_logStore.getDescriptors();
        assertEquals(3, descriptors.size());
        assertEquals("mytarget1", descriptors.get(0).getTargetID());
        assertEquals(1, descriptors.get(0).getStoreID());
        assertEquals(4, descriptors.get(0).getRangeSet().getHigh());

        assertEquals("mytarget1", descriptors.get(1).getTargetID());
        assertEquals(2, descriptors.get(1).getStoreID());
        assertEquals(2, descriptors.get(1).getRangeSet().getHigh());

        assertEquals("mytarget2", descriptors.get(2).getTargetID());
        assertEquals(1, descriptors.get(2).getStoreID());
        assertEquals(5, descriptors.get(2).getRangeSet().getHigh());
    }

    public void testGetDescriptorsSingleLogId() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        List<Descriptor> descriptors = m_logStore.getDescriptors();
        assertEquals(2, descriptors.size());
        assertEquals("mytarget1", descriptors.get(0).getTargetID());
        assertEquals(1, descriptors.get(0).getStoreID());
        assertEquals(4, descriptors.get(0).getRangeSet().getHigh());
        assertEquals("mytarget2", descriptors.get(1).getTargetID());
        assertEquals(1, descriptors.get(1).getStoreID());
        assertEquals(5, descriptors.get(1).getRangeSet().getHigh());
    }

    public void testGetEvents() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        List<Event> events = m_logStore.get(new Descriptor("mytarget1,1,0"));
        assertEquals(3, events.size());
    }

    public void testGetEventsWithRange() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        List<Event> events = m_logStore.get(new Descriptor("mytarget1,1,2"));
        assertEquals(2, events.size());
    }

    public void testPutEvents() throws Exception {
        if (!canRunTest()) {
            return;
        }

        storeEvents();

        assertEquals(5, m_collection.count());
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        try {
            m_collection = m_mongodbService.getDB().getCollection("serverlog");
            // we always get a collection back, regardless if there is an actual MongoDB listening, hence we should do
            // some actual calls that cause a connection to MongoDB to be created...
            if (m_collection.getCount() > 0L) {
                m_collection.remove(new BasicDBObject());
            }
        }
        catch (Exception exception) {
            System.err.println("Mongodb not available on localhost, skipping test...");
            m_collection = null;
        }
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        configureFactory("org.amdatu.mongo", "dbName", "ace");
        configureFactory("org.apache.ace.log.server.store.factory", "name", "serverlog");
    }

    @Override
    protected void doTearDown() throws Exception {
        if (canRunTest()) {
            m_collection.remove(new BasicDBObject());

            CommandResult lastError = m_mongodbService.getDB().getLastError();
            assertNull(lastError.getException());

            assertTrue(m_collection.getCount() == 0L);
        }
    }

    @Override
    protected org.apache.felix.dm.Component[] getDependencies() {
        return new Component[] { createComponent().setImplementation(this)
            .add(createServiceDependency().setService(LogStore.class).setRequired(true))
            .add(createServiceDependency().setService(MongoDBService.class).setRequired(true)) };
    }

    private boolean canRunTest() {
        return m_collection != null;
    }

    private void storeEvents() throws IOException {
        Map<String, String> props = new HashMap<String, String>();
        props.put("myProperty", "myvalue");
        Event event1 = new Event("mytarget1", 1, 1, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event2 = new Event("mytarget1", 1, 2, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event3 = new Event("mytarget2", 1, 3, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event4 = new Event("mytarget2", 1, 5, System.currentTimeMillis(), LogService.LOG_ERROR, props);
        Event event5 = new Event("mytarget1", 1, 4, System.currentTimeMillis(), LogService.LOG_ERROR, props);

        m_logStore.put(Arrays.asList(event1, event2, event3, event4, event5));
    }

}
