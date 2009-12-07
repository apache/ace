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

/**
 * Represents a repository of <ode>StatefulGatewayObject</code>'s.
 */
public interface StatefulGatewayRepository extends ObjectRepository<StatefulGatewayObject> {

    /**
     * Registers a gateway with given attributes. This will result in the creation
     * of a <code>GatewayObject</code> in the <code>GatewayRepository</code>, and
     * the creation of a <code>StatefulGatewayObject</code>, which will also be
     * returned.
     * @param attributes The attributes to create the <code>GatewayObject</code> with.
     * @return The newly registered gateway object.
     */
    public StatefulGatewayObject preregister(Map<String, String> attributes, Map<String, String> tags);

    /**
     * Unregisters a gateway, removing it from the <code>GatewayRepository</code>. Note
     * that a <code>StatefulGatewayObject</code> might stay around if it is backed
     * by audit log entries. If the given ID is not that of an existing <code>GatewayObject</code>,
     * an <code>IllegalArgumentException</code> will be thrown.
     * @param gatewayID A string representing a gateway ID.
     */
    public void unregister(String gatewayID);

    /**
     * Explicitly instruct the <code>StatefulGatewayRepository</code> to update
     * its contents; for instance, after syncing the audit log.
     */
    public void refresh();

}
