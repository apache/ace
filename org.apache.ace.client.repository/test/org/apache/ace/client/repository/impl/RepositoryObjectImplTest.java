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
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link RepositoryObjectImpl}.
 */
public class RepositoryObjectImplTest {
    private static final String XML_NODE = "dummy";

    private ChangeNotifier m_notifier;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_notifier = Mockito.mock(ChangeNotifier.class);
    }

    /**
     * Test case for ACE-463.
     */
    @Test
    public void testKeysOk() throws Exception {
        Map<String, String> attrs = createMap("common", "value1", "attr1", "value2", "attr2", "value3");
        Map<String, String> tags = createMap("common", "value1", "tag1", "value2", "tag2", "value3");

        RepositoryObjectImpl<RepositoryObject> obj = createRepoObject(attrs, tags);
        // Initially, all keys should be present...
        assertKeysContain(obj.keys(), "common", "attr1", "attr2", "tag1", "tag2");
        assertEquals(5, obj.size());

        // Remove a tag...
        obj.removeTag("tag1");
        // Check remaining keys...
        assertKeysContain(obj.keys(), "common", "attr1", "attr2", "tag2");
        assertEquals(4, obj.size());

        // Remove an attribute...
        obj.removeAttribute("attr2");
        // Check remaining keys...
        assertKeysContain(obj.keys(), "common", "attr1", "tag2");
        assertEquals(3, obj.size());

        // Remove a "shared" attribute, should cause it to remain...
        obj.removeAttribute("common");
        // Check remaining keys...
        assertKeysContain(obj.keys(), "common", "attr1", "tag2");
        assertEquals(3, obj.size());

        // Remove a "shared" tag, should cause it to be removed...
        obj.removeTag("common");
        // Check remaining keys...
        assertKeysContain(obj.keys(), "attr1", "tag2");
        assertEquals(2, obj.size());
    }

    private void assertKeysContain(Enumeration<String> enumerator, String... expectedKeys) {
        List<String> expected = new ArrayList<>(Arrays.asList(expectedKeys));

        while (enumerator.hasMoreElements()) {
            String key = enumerator.nextElement();
            assertTrue(expected.remove(key), "Key " + key + " not removed!");
        }

        assertTrue(expected.isEmpty(), "Not all keys were seen in enumerator: " + expected);
    }

    private Map<String, String> createMap(String... entries) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    private RepositoryObjectImpl<RepositoryObject> createRepoObject(Map<String, String> attrs, Map<String, String> tags) {
        return new RepositoryObjectImpl<RepositoryObject>(attrs, tags, m_notifier, XML_NODE) {
            @Override
            String[] getDefiningKeys() {
                // Causes that we always can remove any attribute...
                return new String[0];
            }
        };
    }
}
