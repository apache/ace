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
package org.apache.ace.gogo.queue;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.felix.service.command.Descriptor;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

/**
 * Provides the commands for putting and removing scripts from the queue.
 */
public class QueueCommands {
    public final static String SCOPE = "queue";
    public final static String[] FUNCTIONS = new String[] { "put", "get", "contains" };

    private final BlockingQueue<Dictionary<String, String>> m_queue = new LinkedBlockingQueue<Dictionary<String, String>>();

    @Descriptor("puts a new script definition on the queue")
    public void put(@Descriptor("the script definition to put onto the queue, which consists of a map with at least a 'script' key") Map<String, String> def) throws Exception {
        if (def == null) {
            throw new IllegalArgumentException("Script definition cannot be null!");
        }
        String script = def.get("script");
        if (script == null || "".equals(script.trim())) {
            throw new IllegalArgumentException("Script definition *must* define at least a 'script' property!");
        }
        m_queue.put(toDictionary(def));
    }

    @Descriptor("returns whether anything is present on the queue")
    public boolean contains() throws Exception {
        return contains(null);
    }

    @Descriptor("returns whether anything is present on the queue matchin a given filter definition")
    public boolean contains(@Descriptor("Represents an OSGi-filter to search with") String filter) throws Exception {
        List<Dictionary<String, String>> copy = new ArrayList<Dictionary<String, String>>(m_queue);
        if (filter == null || "".equals(filter.trim())) {
            return !copy.isEmpty();
        }

        Filter f = FrameworkUtil.createFilter(filter);
        for (Dictionary<String, String> entry : copy) {
            if (f.match(entry)) {
                return true;
            }
        }

        return false;
    }

    @Descriptor("returns the first script definition from the queue, if available")
    public Map<String, String> get() throws Exception {
        Dictionary<String, String> dict = m_queue.poll();
        return (dict == null) ? null : toMap(dict);
    }

    @Descriptor("returns the script definition from the queue matching a given filter, if available")
    public Map<String, String>[] get(@Descriptor("Represents an OSGi-filter to match against the script definitions") String filter) throws Exception {
        List<Dictionary<String, String>> copy = new ArrayList<Dictionary<String, String>>(m_queue);

        List<Map<String, String>> result = new ArrayList<Map<String, String>>();

        Filter f = FrameworkUtil.createFilter(filter);
        for (Dictionary<String, String> entry : copy) {
            if (f.match(entry)) {
                result.add(toMap(entry));
                m_queue.remove(entry);
            }
        }

        return result.toArray(new Map[result.size()]);
    }

    private static Dictionary<String, String> toDictionary(Map<String, String> map) {
        Dictionary<String, String> result = new Hashtable<String, String>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, String> toMap(Dictionary<String, String> dict) {
        Map<String, String> result = new HashMap<String, String>();
        Enumeration<String> keys = dict.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            result.put(key, dict.get(key));
        }
        return result;
    }
}
