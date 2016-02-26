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
package org.apache.ace.feedback;

import static org.testng.Assert.*;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.testng.annotations.Test;

/**
 * Test cases for {@link Event}.
 */
public class EventTest {
    private static final String TARGET_ID = "target";
    private static final long STORE_ID = 1234;

    @Test()
    public void testCreateEventFromStringOk() throws Exception {
        String input = "target,1234,1,2,3,key2,value2,key1,value1";

        Event event = new Event(input);

        assertEquals(TARGET_ID, event.getTargetID());
        assertEquals(STORE_ID, event.getStoreID());
        assertEquals(1, event.getID());
        assertEquals(2, event.getTime());
        assertEquals(3, event.getType());

        Map<String, String> props = event.getProperties();
        assertNotNull(props);
        assertFalse(props.isEmpty());

        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));
    }

    @Test()
    public void testCreateEventWithDictionaryOk() throws Exception {
        Event event = new Event(TARGET_ID, STORE_ID, 1, 2, 3, createDict("key1", "value1", "key2", "value2"));

        assertEquals(TARGET_ID, event.getTargetID());
        assertEquals(STORE_ID, event.getStoreID());
        assertEquals(1, event.getID());
        assertEquals(2, event.getTime());
        assertEquals(3, event.getType());

        Map<String, String> props = event.getProperties();
        assertNotNull(props);
        assertFalse(props.isEmpty());

        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));
    }

    @Test()
    public void testCreateEventWithoutPropertiesOk() throws Exception {
        Event event = new Event(TARGET_ID, STORE_ID, 1, 2, 3);

        assertEquals(TARGET_ID, event.getTargetID());
        assertEquals(STORE_ID, event.getStoreID());
        assertEquals(1, event.getID());
        assertEquals(2, event.getTime());
        assertEquals(3, event.getType());

        Map<String, String> props = event.getProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test()
    public void testCreateEventWithPropertiesOk() throws Exception {
        Event event = new Event(TARGET_ID, STORE_ID, 1, 2, 3, createMap("key1", "value1", "key2", "value2"));

        assertEquals(TARGET_ID, event.getTargetID());
        assertEquals(STORE_ID, event.getStoreID());
        assertEquals(1, event.getID());
        assertEquals(2, event.getTime());
        assertEquals(3, event.getType());

        Map<String, String> props = event.getProperties();
        assertNotNull(props);
        assertFalse(props.isEmpty());

        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));
    }

    private Dictionary<String, String> createDict(String... entries) {
        Dictionary<String, String> result = new Hashtable<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    private Map<String, String> createMap(String... entries) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }
}
