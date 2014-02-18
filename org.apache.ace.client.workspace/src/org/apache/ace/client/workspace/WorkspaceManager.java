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
package org.apache.ace.client.workspace;

import java.io.IOException;
import java.util.Map;

/**
 * WorkspaceManager manages workspaces on behalf of a client.
 * 
 * It provides functionality to create, get and remove workspaces.
 * 
 * @see Workspace
 */
@SuppressWarnings("rawtypes")
public interface WorkspaceManager {

    /**
     * Creates a new workspace using the specified session configuration and (optionally) authentication context
     * objects.
     * 
     * @return a new workspace
     * 
     * @throws IOException
     *             in case of I/O problems.
     */
    public Workspace createWorkspace(Map sessionConfiguration, Object... authenticationContext) throws IOException;

    /**
     * Get the workspace with the given identifier.
     * 
     * @param id
     *            the (session) identifier of the workspace to return.
     * @return the workspace with requested ID, or <code>null</code> if no such workspace exists.
     */
    public Workspace getWorkspace(String id) throws IOException;

    /**
     * Removes the workspace with the given identifier.
     * 
     * @param id
     *            the (session) identifier of the workspace to remove.
     * @throws IOException
     *             in case of I/O problems.
     */
    public void removeWorkspace(String id) throws IOException;

    /**
     * Create a new workspace with the default configuration and the default authentication.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @return a new workspace
     * @throws IOException
     *             in case of I/O problems.
     * @see #createWorkspace(Map, Object...)
     */
    public Workspace cw() throws IOException;

    /**
     * Create a new workspace based on the specified session configuration and the default authentication.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param sessionConfiguration
     *            the session configuration
     * @return a new workspace
     * @throws IOException
     *             in case of I/O problems.
     * @see #createWorkspace(Map, Object...)
     */
    public Workspace cw(Map sessionConfiguration) throws IOException;

    /**
     * Create a new workspace, using the specified customer names for the workspace. Otherwise using the default session
     * configuration and the default authentication.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param storeCustomerName
     *            the store customer name
     * @param targetCustomerName
     *            the target customer name
     * @param deploymentCustomerName
     *            the deployment customer name
     * @return a new workspace
     * @throws IOException
     *             in case of I/O problems.
     */
    public Workspace cw(String storeCustomerName, String targetCustomerName, String deploymentCustomerName)
            throws IOException;

    /**
     * Create a new workspace, using the specified customer names for the workspace. Otherwise using the specified
     * session configuration and the default authentication.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param storeCustomerName
     *            the store customer name
     * @param targetCustomerName
     *            the target customer name
     * @param deploymentCustomerName
     *            the deployment customer name
     * @param sessionConfiguration
     *            the session configuration
     * @return a new workspace
     * @throws IOException
     *             in case of I/O problems.
     */
    public Workspace cw(String storeCustomerName, String targetCustomerName, String deploymentCustomerName,
            Map sessionConfiguration) throws IOException;

    /**
     * Get the workspace with the given identifier.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param id
     *            the (session) identifier of the workspace to return.
     * @return the workspace with requested ID, or <code>null</code> if no such workspace exists.
     * @see #getWorkspace(String)
     */
    public Workspace gw(String id) throws IOException;

    /**
     * Removes the workspace with the given identifier.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param id
     *            the (session) identifier of the workspace to remove.
     * @throws IOException
     *             in case of I/O problems.
     * @see #removeWorkspace(String)
     */
    public void rw(String id) throws IOException;

    /**
     * Removes the specified workspace.
     * 
     * Shorthand intended for shell scripting.
     * 
     * @param workspace
     *            the workspace to remove.
     * @throws IOException
     *             in case of I/O problems.
     */
    public void rw(Workspace workspace) throws IOException;

}