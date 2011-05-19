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
package org.apache.ace.nodelauncher;

import java.util.Properties;

/**
 * A TargetLauncher starts, stops and interrogates named nodes. These nodes
 * represent running JVMs in some sense; they can be provided by some
 * cloud-provider, or running JVMs on a single machine.<br>
 * <br>
 * It is up to the provider to decide what to run on the given Node. This can be
 * either a single Management Agent, which can be identified by the <code>id</code>,
 * or a Node Manager.
 */
public interface NodeLauncher {
    /**
     * Starts a new node with the given ID. Does not check whether this ID is already in use.
     * @param id A textual ID for the node.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    void start(String id) throws Exception;

    /**
     * Destroys the node with the given ID. Does not check whether this ID actually exists.
     * @param id A textual ID for the node.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    void stop(String id) throws Exception;
    
    /**
     * Retrieves properties from the node. These will include, at least
     * <ul>
     * <li><em>ip</em> The public IP address of the node.</li>
     * </ul>
     * @param id The textual ID for the node.
     * @return the properties of the node, or <code>null</code> if this node cannot be found.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    Properties getProperties(String id) throws Exception;
}
