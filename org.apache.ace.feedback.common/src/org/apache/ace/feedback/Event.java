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

import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ace.feedback.util.Codec;

/**
 * Event from specific target (in a specific store).
 */
public class Event implements Comparable<Object> {
    private final String m_targetID;
    private final long m_storeID;
    private final long m_id;
    private final long m_time;
    private final int m_type;
    private final Map<String, String> m_properties;

    public Event(String targetID, long storeID, long id, long time, int type) {
        this(targetID, storeID, id, time, type, Collections.<String, String> emptyMap());
    }

    public Event(String targetID, long storeID, long id, long time, int type, Map<String, String> properties) {
        m_targetID = targetID;
        m_storeID = storeID;
        m_id = id;
        m_time = time;
        m_type = type;
        m_properties = properties;
    }

    public Event(String targetID, long storeID, long id, long time, int type, Dictionary<String, String> dictionary) {
        m_targetID = targetID;
        m_storeID = storeID;
        m_id = id;
        m_time = time;
        m_type = type;
        m_properties = new HashMap<>();

        Enumeration<String> keys = dictionary.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            m_properties.put(key, dictionary.get(key));
        }
    }

    public Event(String targetID, Event source) {
        this(targetID, source.getStoreID(), source.getID(), source.getTime(), source.getType(), source.getProperties());
    }

    public Event(byte[] representation) {
        this(new String(representation));
    }

    public Event(String representation) {
        try {
            StringTokenizer st = new StringTokenizer(representation, ",");
            m_targetID = Codec.decode(st.nextToken());
            m_storeID = Long.parseLong(st.nextToken());
            m_id = Long.parseLong(st.nextToken());
            m_time = Long.parseLong(st.nextToken());
            m_type = Integer.parseInt(st.nextToken());
            m_properties = new HashMap<>();
            while (st.hasMoreTokens()) {
                m_properties.put(st.nextToken(), Codec.decode(st.nextToken()));
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not create event from: " + representation, e);
        }
    }

    public String toRepresentation() {
        StringBuffer result = new StringBuffer();
        result.append(Codec.encode(m_targetID));
        result.append(',');
        result.append(m_storeID);
        result.append(',');
        result.append(m_id);
        result.append(',');
        result.append(m_time);
        result.append(',');
        result.append(m_type);
        for (String key : m_properties.keySet()) {
            result.append(',');
            result.append(key);
            result.append(',');
            result.append(Codec.encode(m_properties.get(key)));
        }
        return result.toString();
    }

    /**
     * Returns the unique ID of the target that created this event.
     */
    public String getTargetID() {
        return m_targetID;
    }

    /**
     * Returns the unique storeID. This ID is unique within a target.
     */
    public long getStoreID() {
        return m_storeID;
    }

    /**
     * Return the ID or sequence number of the event.
     */
    public long getID() {
        return m_id;
    }

    /**
     * Returns the timestamp of the event.
     */
    public long getTime() {
        return m_time;
    }

    /**
     * Returns the type of the event. Valid types are defined in this interface.
     */
    public int getType() {
        return m_type;
    }

    /**
     * Returns the properties of the event. Properties are restricted to simple key value pairs, only a couple of types
     * are allowed: String, int, long, boolean (TODO what do we need?).
     */
    public Map<String, String> getProperties() {
        return m_properties;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Event) {
            Event event = (Event) o;
            return m_targetID.equals(event.m_targetID)
                && m_storeID == event.m_storeID && m_id == event.m_id;
        }

        return false;
    }

    public int hashCode() {
        return (int) (m_targetID.hashCode() + m_storeID + m_id);
    }

    public int compareTo(Object o) {
        Event e = (Event) o;
        final int cmp = m_targetID.compareTo(e.m_targetID);
        if (cmp != 0) {
            return cmp;
        }
        if (m_storeID < e.m_storeID) {
            return -1;
        }
        if (m_storeID > e.m_storeID) {
            return 1;
        }
        if (m_id < e.m_id) {
            return -1;
        }
        if (m_id > e.m_id) {
            return 1;
        }
        return 0;
    }
}
