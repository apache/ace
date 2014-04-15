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
package org.apache.ace.log.server.store.mongo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.amdatu.mongo.MongoDBService;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.range.Range;
import org.apache.ace.range.SortedRangeSet;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;

public class MongoLogStore implements LogStore, ManagedService {
    private static final String MAXIMUM_NUMBER_OF_EVENTS = "MaxEvents";

    private final String m_logname;
    private volatile MongoDBService m_mongoDBService;
    private int m_maxEvents = 0;

    public MongoLogStore(String logname) {
        this.m_logname = logname;
    }

    @Override
    public List<Event> get(Descriptor range) throws IOException {
        DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);
        long high = range.getRangeSet().getHigh();

        BasicDBObject filter = new BasicDBObject().append("targetId", range.getTargetID()).append("logId", range.getStoreID());
        if (high > 0) {
            filter.append("id", new BasicDBObject("$lte", high));
        }

        DBCursor cursor = collection.find(filter);
        cursor.sort(new BasicDBObject("id", 1));

        List<Event> Events = new ArrayList<Event>();
        while (cursor.hasNext()) {
            DBObject event = cursor.next();
            String targetId = (String) event.get("targetId");
            long logId = (Long) event.get("logId");
            long id = (Long) event.get("id");
            long time = (Long) event.get("time");
            int type = (Integer) event.get("type");
            
            Map<String, String> properties = new HashMap<String, String>();
            DBObject propertiesDbObject = (DBObject) event.get("properties");
            for (String key : propertiesDbObject.keySet()) {
                properties.put(key, (String) propertiesDbObject.get(key));
            }

            Events.add(new Event(targetId, logId, id, time, type, properties));
        }

        return Events;
    }

    @Override
    public Descriptor getDescriptor(String targetID, long logID)
        throws IOException {

        DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);

        BasicDBObject filter = new BasicDBObject().append("targetId", targetID)
            .append("logId", logID);

        DBCursor cursor = collection.find(filter);
        cursor.sort(new BasicDBObject("id", -1));

        long high = 1;
        if (cursor.hasNext()) {
            DBObject row = cursor.next();
            high = (Long) row.get("id");
            return new Descriptor(targetID, logID, new SortedRangeSet(
                new Range(1, high).toRepresentation()));
        }
        else {
            return new Descriptor(targetID, logID, SortedRangeSet.FULL_SET);
        }
    }

    @Override
    public void put(List<Event> events) throws IOException {
        // TODO : if m_max_events > 0 then make sure there are no more than m_maxEvents
        DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);

        for (Event event : events) {
            DBObject dbObject = new BasicDBObject()
                .append("targetId", event.getTargetID())
                .append("logId", event.getStoreID())
                .append("id", event.getID())
                .append("time", event.getTime())
                .append("type", event.getType())
                .append("properties", event.getProperties());

            collection.save(dbObject);
        }
    }

    @Override
    public List<Descriptor> getDescriptors(String targetID)
        throws IOException {

        DBCollection collection = m_mongoDBService.getDB().getCollection(m_logname);
        String m = "function() {emit(this.targetId,this.logId);}";
        String r = "function(k, vals) {var result = {target: k, logIds: []}; vals.forEach(function(value) { result.logIds.push(value)}); return result;}";
        DBObject filter = new BasicDBObject();
        if (targetID != null) {
            filter.put("targetId", targetID);
        }
        MapReduceOutput mapReduce = collection.mapReduce(m, r, null, OutputType.INLINE, filter);
        Iterator<DBObject> iterator = mapReduce.results().iterator();

        List<Descriptor> descriptors = new ArrayList<Descriptor>();
        while (iterator.hasNext()) {
            DBObject row = iterator.next();
            DBObject value = (DBObject) row.get("value");
            String targetId = (String) value.get("target");
            @SuppressWarnings("unchecked")
            List<Long> logIds = (List<Long>) value.get("logIds");
            Set<Long> logIdsFiltered = new HashSet<Long>();
            logIdsFiltered.addAll(logIds);

            for (long logId : logIdsFiltered) {
                descriptors.add(getDescriptor(targetId, logId));
            }
        }

        return descriptors;
    }

    @Override
    public List<Descriptor> getDescriptors() throws IOException {
        return getDescriptors(null);
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            String maximumNumberOfEvents = (String) settings.get(MAXIMUM_NUMBER_OF_EVENTS);
            if (maximumNumberOfEvents != null) {
                try {
                    m_maxEvents = Integer.parseInt(maximumNumberOfEvents);
                } catch (NumberFormatException nfe) {
                    throw new ConfigurationException(MAXIMUM_NUMBER_OF_EVENTS, "is not a number");
                }
            }
        }
    }

    @Override
    public void clean() throws IOException {
        // TODO : if m_max_events > 0 then remove all events from the mongo store where there are more than m_maxEvents
    }
}
