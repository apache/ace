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
package org.apache.ace.log.target.store;

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;

import org.apache.ace.feedback.Event;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Server log store interface for the targets. Implementations of this service interface provide a persisted storage for
 * log data.
 */
@ProviderType
public interface LogStore
{

    /**
     * Create a new event out of the given type and properties. Write it to the store and return it.
     *
     * @param type the type of the event.
     * @param props the properties of the event.
     * @return the created event that has been persisted.
     * @throws java.io.IOException in case of any IO error.
     */
    public Event put(int type, Dictionary props) throws IOException;

    /**
     * Get all events in the given log.
     *
     * @param logID the id of the log.
     * @return a list of LogEvent's that are currently in the log of the given logID.
     * @throws java.io.IOException in case of any IO error.
     */
    public List/*<Event>*/get(long logID) throws IOException;

    /**
     * Get the events in the given log that are in the range of the given lower and upper bound.
     *
     * @param logID the id of the log.
     * @param from the lower bound.
     * @param to the upper bound.
     * @return a list of LogEvent's that are currently in the log of the given logID and have an id in the range of the given
     *         bounds.
     * @throws java.io.IOException in case of any IO error.
     */
    public List/*<Event>*/get(long logID, long from, long to) throws IOException;

    /**
     * Get the the highest id of any LogEvent entry in the given log.
     *
     * @param logID the id of the log.
     * @return the id of the highest LogEvent entry in the given log.
     * @throws java.io.IOException in case of any IO error.
     */
    public long getHighestID(long logID) throws IOException;

    /**
     * Get the ids of all available logs in this store.
     *
     * @return an array of the ids of all available logs in this store.
     * @throws java.io.IOException in case of any IO error.
     */
    public long[] getLogIDs() throws IOException;
}