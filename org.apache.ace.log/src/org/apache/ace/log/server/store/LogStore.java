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
package org.apache.ace.log.server.store;

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Log store interface. Implementation of this service interface provide a persisted storage for Event logs.
 */
@ProviderType
public interface LogStore
{

    /**
     * Event topic that indicates a new Event that has been added to the store. The name
     * of the log is available as EVENT_PROP_LOGNAME, the original Event as EVENT_PROP_LOG_EVENT.
     */
    public static final String EVENT_TOPIC = LogStore.class.getName().replace('.', '/') + "/Event";

    /**
     * Event property key containing the name of the log on which the Event has been added.
     */
    public static final String EVENT_PROP_LOGNAME = "name";

    /**
     * Event property key containing the Event that has been added.
     */
    public static final String EVENT_PROP_LOG_EVENT = "Event";

    /**
     * Return all events in a given range.
     *
     * @param range the range to filter events by.
     * @return a list of all events in this store that are in the given range.
     * @throws java.io.IOException in case of any error.
     */
    public List<Event> get(Descriptor range) throws IOException;

    /**
     * Get the range for the given id and the given log.
     *
     * @param targetID the id for which to return the log range.
     * @param logID the log id for which to return the range.
     * @return the range for the given id and the given log.
     * @throws java.io.IOException in case of any error.
     */
    public Descriptor getDescriptor(String targetID, long logID) throws IOException;

    /**
     * Store the given events. The implementation does not have to be transactional i.e., it might throw an exception and still
     * store part of the events. However, individual events should be either stored or not.
     *
     * @param events a list of events to store.
     * @throws java.io.IOException in case of any error. It might be possible that only part of the events get stored.
     */
    public void put(List<Event> events) throws IOException;

    /**
     * Get the ranges of all logs of the given id.
     *
     * @param targetID the id for which to return the log ranges.
     * @return a list of the ranges of all logs for the given id.
     * @throws java.io.IOException in case of any error.
     */
    public List<Descriptor> getDescriptors(String targetID) throws IOException;

    /**
     * Get the ranges of all logs of all ids in this store.
     *
     * @return a list of ranges of all logs for all ids in this store.
     * @throws java.io.IOException in case of any error.
     */
    public List<Descriptor> getDescriptors() throws IOException;
    
    /**
     * Cleanup the events in the store. This method will check each target and log in this store and remove all
     * events exceeding the maximum number of events that can be configured for this store.
     * 
     * @throws IOException in case of any error
     */
    public void clean() throws IOException;
    
    /**
     * Create a new event out of the given type and properties. Write it to the store and return it.
     *
     * @param targetID the targetID of this event.
     * @param type the type of the event.
     * @param props the properties of the event.
     * @return the created event that has been persisted.
     * @throws java.io.IOException in case of any IO error.
     */
    public Event put(String targetID, int type, Dictionary props) throws IOException;
    
    public void setLowestID(String targetID, long logID, long lowestID) throws IOException;
    public long getLowestID(String targetID, long logID) throws IOException;
}