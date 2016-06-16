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
package org.apache.ace.client.repository;

import java.util.List;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents a basic association between two Associatable objects, whose types
 * are given by the generic parameters.
 */
@ProviderType
public interface Association<L extends Associatable, R extends Associatable> extends RepositoryObject {
    /**
     * A filter string indicating the left endpoint.
     */
    public final static String LEFT_ENDPOINT = "leftEndpoint";
    /**
     * A filter string indicating the right endpoint.
     */
    public final static String RIGHT_ENDPOINT = "rightEndpoint";
    /**
     * A string indicating the cardinality for the left endpoint.
     */
    public final static String LEFT_CARDINALITY = "leftCardinality";
    /**
     * A string indicating the cardinality for the right endpoint.
     */
    public final static String RIGHT_CARDINALITY = "rightCardinality";

    /**
     * Used for <code>Event</code> properties: A <code>List&lt;Associatable&gt;</code> indicating the
     * objects making up this endpoint before the event happened.
     */
    public final static String EVENT_OLD = "old";
    /**
     * Used for <code>Event</code> properties: A <code>List&lt;Associatable&gt;</code> indicating the
     * objects making up this endpoint before the event happened.
     */
    public final static String EVENT_NEW = "new";

    /**
     * Gets the Associatable object on the 'other side' of <code>from</code>. If
     * <code>from</code> does not point to either end of this association, this
     * function will return <code>null</code>.
     */
    public List<Associatable> getTo(Associatable from);
    /**
     * Returns the 'left' side of this association.
     */
    public List<L> getLeft();
    /**
     * Returns the 'left' side of this association.
     */
    public List<R> getRight();
    /**
     * Removes this association from both endpoints.
     */
    public void remove();
    /**
     * Indicates whether both sides of this association are available. In an m-to-n assocation,
     * this function will indicate whether at least one object is available on either side.
     */
    public boolean isSatisfied();
}