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

import java.io.IOException;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Log synchronizing interface. It is intended to give direct access to the synchronizing
 * possibilities of the server side log.
 */
@ProviderType
public interface LogSync
{

    /**
     * Pushes local changes and updates them in the remote repository.
     * @throws java.io.IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean push() throws IOException;

    /**
     * Pulls remote changes and updates them in the local repository.
     * @throws java.io.IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean pull() throws IOException;

    /**
     * Both pushes and pulls changes, and updates them in the both repositories.
     * @throws java.io.IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean pushpull() throws IOException;
    
    /** Pushes lowest IDs to remote repository. */
    public boolean pushIDs() throws IOException;
    /** Pulls lowest IDs from remote repository. */
    public boolean pullIDs() throws IOException;
    /** Pushes and pulls lowest IDs to/from remote repository. */
    public boolean pushpullIDs() throws IOException;

    /**
     * Returns the name of the log 'channel' this log sync task is assigned to.
     *
     * @return The name of the log 'channel' this log sync task is assigned to.
     */
    public String getName();
}