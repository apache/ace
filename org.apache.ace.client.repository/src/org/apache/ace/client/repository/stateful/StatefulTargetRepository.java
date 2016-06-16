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
package org.apache.ace.client.repository.stateful;

import java.util.Map;

import org.apache.ace.client.repository.ObjectRepository;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents a repository of <ode>StatefulTargetObject</code>'s.
 */
@ProviderType
public interface StatefulTargetRepository extends ObjectRepository<StatefulTargetObject> {

    /**
     * Registers a target with given attributes. This will result in the creation
     * of a <code>TargetObject</code> in the <code>TargetRepository</code>, and
     * the creation of a <code>StatefulTargetObject</code>, which will also be
     * returned.
     * @param attributes The attributes to create the <code>TargetObject</code> with.
     * @return The newly registered target object.
     */
    public StatefulTargetObject preregister(Map<String, String> attributes, Map<String, String> tags);

    /**
     * Unregisters a target, removing it from the <code>TargetRepository</code>. Note
     * that a <code>StatefulTargetObject</code> might stay around if it is backed
     * by audit log entries. If the given ID is not that of an existing <code>TargetObject</code>,
     * an <code>IllegalArgumentException</code> will be thrown.
     * @param targetID A string representing a target ID.
     */
    public void unregister(String targetID);

    /**
     * Explicitly instruct the <code>StatefulTargetRepository</code> to update
     * its contents; for instance, after syncing the audit log.
     */
    public void refresh();

}
