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
package org.apache.ace.log;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.ace.util.Codec;


/**
 * Log event from a specific gateway and log.
 */
public class LogEvent implements Comparable {
    
    private final String m_gatewayID;
    private final long m_logID;
    private final long m_id;
    private final long m_time;
    private final int m_type;
    private final Dictionary m_properties;

    public LogEvent(String gatewayID, long logID, long id, long time,
        int type, Dictionary properties) {
        m_gatewayID = gatewayID;
        m_logID = logID;
        m_id = id;
        m_time = time;
        m_type = type;
        m_properties = properties;
    }

    public LogEvent(String representation) {
        try {
            StringTokenizer st = new StringTokenizer(representation, ",");
            m_gatewayID = Codec.decode(st.nextToken());
            m_logID = Long.parseLong(st.nextToken());
            m_id = Long.parseLong(st.nextToken());
            m_time = Long.parseLong(st.nextToken());
            m_type = Integer.parseInt(st.nextToken());
            m_properties = new Properties();
            while (st.hasMoreTokens()) {
                m_properties.put(st.nextToken(), Codec.decode(st.nextToken()));
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException(
                "Could not create log event from: " + representation);
        }
    }

    public String toRepresentation() {
        StringBuffer result = new StringBuffer();
        result.append(Codec.encode(m_gatewayID));
        result.append(',');
        result.append(m_logID);
        result.append(',');
        result.append(m_id);
        result.append(',');
        result.append(m_time);
        result.append(',');
        result.append(m_type);
        Enumeration e = m_properties.keys();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            result.append(',');
            result.append(key);
            result.append(',');
            result.append(Codec.encode((String) m_properties.get(key)));
        }
        return result.toString();
    }

    /**
     * Returns the unique ID of the gateway that created this event.
     */
    public String getGatewayID() {
        return m_gatewayID;
    }

    /**
     * Returns the unique log ID of the log. This ID is unique within a gateway.
     */
    public long getLogID() {
        return m_logID;
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
     * Returns the properties of the event. Properties are restricted to simple key value pairs, only a couple of types are allowed: String, int, long, boolean (TODO what do we need?).
     */
    public Dictionary getProperties() {
        return m_properties;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof LogEvent) {
            LogEvent event = (LogEvent) o;
            return m_gatewayID.equals(event.m_gatewayID)
                && m_logID == event.m_logID && m_id == event.m_id;
        }

        return false;
    }
    
    public int hashCode() {
        return (int) (m_gatewayID.hashCode() + m_logID + m_id); 
    }

    public int compareTo(Object o) {
        LogEvent e = (LogEvent) o;
        final int cmp = m_gatewayID.compareTo(e.m_gatewayID);
        if (cmp != 0) {
            return cmp;
        }
        if (m_logID < e.m_logID) {
            return -1;
        }
        if (m_logID > e.m_logID) {
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
