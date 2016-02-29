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
package org.apache.ace.log.server.store.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.event.EventAdmin;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ServerLogStoreTester {
    private static final String MAXIMUM_NUMBER_OF_EVENTS = "MaxEvents";

    private LogStoreImpl m_logStore;
    private File m_dir;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_dir = File.createTempFile("logstore", "txt");
        m_dir.delete();
        m_dir.mkdirs();
        m_logStore = new LogStoreImpl(m_dir, "log");
        TestUtils.configureObject(m_logStore, EventAdmin.class);
        m_logStore.start();
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws IOException {
        delete(m_dir);
    }

    @Test()
    public void testLog() throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";
        List<Event> events = new ArrayList<>();
        for (String target : new String[] { "g1", "g2", "g3" }) {
            for (long log : new long[] { 1, 2, 3, 5 }) {
                for (long id : new long[] { 1, 2, 3, 20 }) {
                    events.add(new Event(target, log, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, props));
                }
            }
        }
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 3 * 4 : "Incorrect amount of ranges returned from store";
        List<Event> stored = getStoredEvents();

        Set<String> in = new HashSet<>();
        for (Event event : events) {
            in.add(event.toRepresentation());
        }
        Set<String> out = new HashSet<>();
        for (Event event : stored) {
            out.add(event.toRepresentation());
        }
        assert in.equals(out) : "Stored events differ from the added.";
    }

    @Test()
    public void testLogOutOfOrder() throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";

        List<Event> events = new ArrayList<>();
        events.add(new Event("t1", 1, 2, 2, AuditEvent.FRAMEWORK_STARTED, props));
        events.add(new Event("t1", 1, 3, 3, AuditEvent.FRAMEWORK_STARTED, props));
        events.add(new Event("t1", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 1 : "Incorrect amount of ranges returned from store";
        List<Event> stored = getStoredEvents();

        Set<String> out = new HashSet<>();
        for (Event event : stored) {
            out.add(event.toRepresentation());
        }
        assert out.size() == 3 : "Stored events differ from the added.";
    }

    @Test()
    public void testLogOutOfOrderOneByOne() throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";

        List<Event> events = new ArrayList<>();
        events.add(new Event("t1", 1, 2, 2, AuditEvent.FRAMEWORK_STARTED, props));
        m_logStore.put(events);
        events.clear();
        events.add(new Event("t1", 1, 3, 3, AuditEvent.FRAMEWORK_STARTED, props));
        m_logStore.put(events);
        events.clear();
        events.add(new Event("t1", 1, 1, 1, AuditEvent.FRAMEWORK_STARTED, props));
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 1 : "Incorrect amount of ranges returned from store";
        List<Event> stored = getStoredEvents();

        Set<String> out = new HashSet<>();
        for (Event event : stored) {
            out.add(event.toRepresentation());
        }
        assert out.size() == 3 : "Stored events differ from the added: " + out.size();
    }

    @Test()
    public void testLogLowestID() throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";
        List<Event> events = new ArrayList<>();

        assert 0 == m_logStore.getLowestID("target", 1) : "Lowest ID should be 0 by default, not: " + m_logStore.getLowestID("target", 1);
        m_logStore.setLowestID("target", 1, 10);
        assert 10 == m_logStore.getLowestID("target", 1) : "Lowest ID should be 10, not: " + m_logStore.getLowestID("target", 1);
        assert 0 == m_logStore.getLowestID("target", 0) : "Lowest ID should be 0 by default, not: " + m_logStore.getLowestID("target", 1);
        assert 0 == m_logStore.getLowestID("target2", 1) : "Lowest ID should be 0 by default, not: " + m_logStore.getLowestID("target", 1);

        for (long id = 1; id <= 20; id++) {
            events.add(new Event("target", 1, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, props));
        }
        m_logStore.put(events);
        List<Descriptor> descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        String range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("10-20") : "Incorrect range in descriptor: " + range;
        List<Event> stored = getStoredEvents();
        assert stored.size() == 11 : "Exactly 11 events should have been stored";
        m_logStore.setLowestID("target", 1, 20);
        stored = getStoredEvents();
        assert stored.size() == 1 : "Exactly 1 event should have been stored";
        descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("20") : "Incorrect range in descriptor: " + range;
        m_logStore.setLowestID("target", 1, 21);
        stored = getStoredEvents();
        assert stored.size() == 0 : "No events should have been stored";
        descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("") : "Incorrect range in descriptor: " + range;
        m_logStore.setLowestID("target", 1, 100);
        stored = getStoredEvents();
        assert stored.size() == 0 : "No events should have been stored";
    }

    @Test()
    public void testLogIDGenerationWithLowestID() throws IOException {
        Dictionary<String, String> props = new Hashtable<>();
        props.put("test", "foo");

        long logID = 0;
        for (long id = 1; id <= 20; id++) {
            Event event = m_logStore.put("target", 1, props);
            System.out.println("Event: " + event.toRepresentation());
            logID = event.getStoreID();
        }

        List<Descriptor> descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        String range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("1-20") : "Incorrect range in descriptor: " + range;
        List<Event> stored = getStoredEvents();
        assert stored.size() == 20 : "Exactly 20 events should have been stored";

        m_logStore.setLowestID("target", logID, 10);
        assert 10 == m_logStore.getLowestID("target", logID) : "Lowest ID should be 10, not: " + m_logStore.getLowestID("target", logID);

        stored = getStoredEvents();
        assert stored.size() == 11 : "Exactly 11 events should have been stored, we found " + stored.size();
        descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("10-20") : "Incorrect range in descriptor: " + range;

        m_logStore.setLowestID("target", logID, 21);
        stored = getStoredEvents();
        assert stored.size() == 0 : "No events should have been stored, we found " + stored.size();
        descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("") : "Incorrect range in descriptor: " + range;

        for (long id = 1; id <= 20; id++) {
            System.out.println("Event: " + m_logStore.put("target", 1, props).toRepresentation());
        }

        stored = getStoredEvents();
        assert stored.size() == 20 : "Exactly 20 events should have been stored, we found " + stored.size();
        descriptors = m_logStore.getDescriptors();
        assert descriptors.size() == 1 : "Incorrect amount of ranges returned from store";
        range = descriptors.get(0).getRangeSet().toRepresentation();
        assert range.equals("21-40") : "Incorrect range in descriptor: " + range;
    }

    private List<Event> getStoredEvents() throws IOException {
        List<Event> stored = new ArrayList<>();
        for (Descriptor range : m_logStore.getDescriptors()) {
            System.out.println("TID: " + range.getTargetID());
            for (Descriptor range2 : m_logStore.getDescriptors(range.getTargetID())) {
                stored.addAll(m_logStore.get(m_logStore.getDescriptor(range2.getTargetID(), range2.getStoreID())));
                System.out.println("  Range: " + range2.getRangeSet());
            }
        }
        return stored;
    }

    @Test()
    public void testCreateLogMessagesConcurrently() throws Exception {
        final Properties props = new Properties();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";
        ExecutorService exec = Executors.newFixedThreadPool(10);
        for (final String target : new String[] { "g1", "g2", "g3", "g4", "g5", "g6", "g7", "g8", "g9", "g10" }) {
            exec.execute(new Runnable() {
                public void run() {
                    for (long id = 0; id < 1000; id++) {
                        try {
                            m_logStore.put(target, 1, props);
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            });
        }
        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);
        assert m_logStore.getDescriptors().size() == 10 : "Incorrect amount of ranges returned from store: " + m_logStore.getDescriptors().size();
        List<Event> stored = getStoredEvents();
        assert stored.size() == 10000 : "Incorrect number of events got stored: " + stored.size();
    }

    @Test()
    public void testLogWithSpecialCharacters() throws IOException {
        String targetID = "myta\0rget";
        Event event = new Event(targetID, 1, 1, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED);
        List<Event> events = new ArrayList<>();
        events.add(event);
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 1 : "Incorrect amount of ranges returned from store: expected 1, found " + m_logStore.getDescriptors().size();
        assert m_logStore.getDescriptors(targetID).size() == 1 : "We expect to find a single event: expected 1, found " + m_logStore.getDescriptors(targetID).size();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test()
    public void testMaximumNumberOfEvents() throws Exception {
        Dictionary settings = new Properties();
        settings.put(MAXIMUM_NUMBER_OF_EVENTS, "1");
        m_logStore.updated(settings);

        List<Event> events = new ArrayList<>();
        for (String target : new String[] { "target" }) {
            for (long log : new long[] { 1 }) {
                for (long id : new long[] { 1, 2 }) {
                    events.add(new Event(target, log, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, new HashMap<String, String>()));
                }
            }
        }

        m_logStore.put(events);

        List<Descriptor> allDescriptors = m_logStore.getDescriptors();
        assert allDescriptors.size() == 1 : "Expected only one descriptor, found: " + allDescriptors.size();
        for (Descriptor range : allDescriptors) {
            List<Descriptor> allLogsForTarget = m_logStore.getDescriptors(range.getTargetID());
            for (Descriptor range2 : allLogsForTarget) {
                List<Event> getEvents = m_logStore.get(m_logStore.getDescriptor(range2.getTargetID(), range2.getStoreID()));
                assert getEvents.size() == 1 : "Only one event expected, found " + getEvents.size();
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test()
    public void testMaximumNumberOfEventsMultipleLogs() throws Exception {
        Dictionary settings = new Properties();
        settings.put(MAXIMUM_NUMBER_OF_EVENTS, "1");
        m_logStore.updated(settings);

        List<Event> events = new ArrayList<>();
        for (String target : new String[] { "target" }) {
            for (long log : new long[] { 1, 2 }) {
                for (long id : new long[] { 1, 2 }) {
                    events.add(new Event(target, log, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, new HashMap<String, String>()));
                }
            }
        }

        m_logStore.put(events);
        List<Descriptor> allDescriptors = m_logStore.getDescriptors();
        assert allDescriptors.size() == 2 : "Expected two descriptor, found: " + allDescriptors.size();
        for (Descriptor range : allDescriptors) {
            List<Descriptor> allLogsForTarget = m_logStore.getDescriptors(range.getTargetID());
            for (Descriptor range2 : allLogsForTarget) {
                List<Event> getEvents = m_logStore.get(m_logStore.getDescriptor(range2.getTargetID(), range2.getStoreID()));
                assert getEvents.size() == 1 : "Only one event expected, found " + getEvents.size();
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test()
    public void testClean() throws Exception {
        List<Event> events = new ArrayList<>();
        for (String target : new String[] { "target" }) {
            for (long log : new long[] { 1, 2 }) {
                for (long id : new long[] { 1, 2, 3, 4 }) {
                    events.add(new Event(target, log, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, new HashMap<String, String>()));
                }
            }
        }
        m_logStore.put(events);

        Dictionary settings = new Properties();
        settings.put(MAXIMUM_NUMBER_OF_EVENTS, "1");
        m_logStore.updated(settings);

        m_logStore.clean();
        List<Descriptor> allDescriptors = m_logStore.getDescriptors();
        assert allDescriptors.size() == 2 : "Expected two descriptor, found: " + allDescriptors.size();
        for (Descriptor range : allDescriptors) {
            List<Descriptor> allLogsForTarget = m_logStore.getDescriptors(range.getTargetID());
            for (Descriptor range2 : allLogsForTarget) {
                List<Event> getEvents = m_logStore.get(m_logStore.getDescriptor(range2.getTargetID(), range2.getStoreID()));
                assert getEvents.size() == 1 : "Only one event expected, found " + getEvents.size();
            }
        }
    }

    @Test()
    public void testConcurrentLog() throws IOException, InterruptedException {
        ExecutorService es = Executors.newFixedThreadPool(8);
        final Map<String, String> props = new HashMap<>();
        props.put("test", "bar");

        List<Descriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";
        for (String target : new String[] { "g1", "g2", "g3" }) {
            for (long log : new long[] { 1, 2, 3, 5 }) {
                for (long id = 0; id < 500; id++) {
                    final String t = target;
                    final long l = log;
                    final long i = id;
                    es.execute(new Runnable() {
                        @Override
                        public void run() {
                            List<Event> list = new ArrayList<>();
                            list.add(new Event(t, l, i, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, props));
                            try {
                                m_logStore.put(list);
                            }
                            catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                        }
                    });
                }
            }
        }
        es.shutdown();
        es.awaitTermination(60, TimeUnit.SECONDS);
        int size = m_logStore.getDescriptors().size();
        assert size == 3 * 4 : "Incorrect amount of ranges returned from store: " + size;
        List<Event> stored = getStoredEvents();

        Set<String> out = new HashSet<>();
        for (Event event : stored) {
            out.add(event.toRepresentation());
        }
    }

    private void delete(File root) {
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                delete(child);
            }
        }
        root.delete();
    }
}
