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
package org.apache.ace.gogo.log;

import org.apache.ace.feedback.Event;
import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.service.command.Descriptor;

import java.util.ArrayList;
import java.util.List;

public class LogCommands {

    public final static String SCOPE = "ace-log";
    public final static String[] FUNCTIONS = new String[] { "list", "getAll", "get", "cleanup" };

    // Injected by Felix DM...
    private volatile LogStore m_logStore;

    @Descriptor("Get all Descriptors of TargetID and StoreID.")
    public List<org.apache.ace.feedback.Descriptor> list() throws Exception {
        return m_logStore.getDescriptors();
    }

    @Descriptor("Get all Descriptors of a particular TargetID.")
    public List<org.apache.ace.feedback.Descriptor> list(
        @Descriptor("targetId - Identifier of the Target.") String targetID) throws Exception {
        return m_logStore.getDescriptors(targetID);
    }

    @Descriptor("Get all the Log Events available. USE WITH EXTREME CARE!")
    public List<Event> getAll() throws Exception {
        ArrayList<Event> result = new ArrayList<>();
        for (org.apache.ace.feedback.Descriptor descriptor : list()) {
            result.addAll(m_logStore.get(descriptor));
        }
        return result;
    }

    @Descriptor("Get all the Log Events from the given TargetID")
    public List<Event> get(
        @Descriptor("targetId - Identifier of the Target.") String targetId) throws Exception {
        ArrayList<Event> result = new ArrayList<>();
        for (org.apache.ace.feedback.Descriptor descriptor : list(targetId)) {
            result.addAll(m_logStore.get(descriptor));
        }
        return result;
    }

    @Descriptor("Get all the Log Events in the given TargetID and StoreID.")
    public List<Event> get(
        @Descriptor("targetId - Identifier of the Target.") String targetId,
        @Descriptor("  storeId  - Identity of the Store on the given Target.") long storeId) throws Exception {
        return m_logStore.get(m_logStore.getDescriptor(targetId, storeId));
    }

    @Descriptor("Get all the Log Events from the given TargetID and StoreID, after a given time.")
    public List<Event> get(
        @Descriptor("targetId - Identifier of the Target.") String targetId,
        @Descriptor("  storeId  - Identity of the Store on the given Target.") long storeId,
        @Descriptor("  start    - The starting time of the time range requested.(millis)") long start) throws Exception {
        ArrayList<Event> result = new ArrayList<>();
        org.apache.ace.feedback.Descriptor descriptor = m_logStore.getDescriptor(targetId, storeId);
        for (Event event : m_logStore.get(descriptor)) {
            if (event.getTime() >= start) {
                result.add(event);
            }
        }
        return result;
    }

    @Descriptor("Get all the Log Events from the given TargetID and StoreID, within a time range.")
    public List<Event> get(
        @Descriptor("targetId - Identifier of the Target.") String targetId,
        @Descriptor("  storeId  - Identity of the Store on the given Target.") long storeId,
        @Descriptor("  start    - The starting time of the time range requested.(millis)") long start,
        @Descriptor("  end      - The ending time of the time range requested. (millis)") long end) throws Exception {
        ArrayList<Event> result = new ArrayList<>();
        org.apache.ace.feedback.Descriptor descriptor = m_logStore.getDescriptor(targetId, storeId);
        for (Event event : m_logStore.get(descriptor)) {
            long timeOfEvent = event.getTime();
            if (timeOfEvent >= start && timeOfEvent <= end) {
                result.add(event);
            }
        }
        return result;
    }

    @Descriptor("Apply the configured maximum to all existing logs")
    public void cleanup() throws Exception {
        m_logStore.clean();
        System.out.println("All logfiles processed");
    }
}
