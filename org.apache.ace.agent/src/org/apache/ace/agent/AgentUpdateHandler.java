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
package org.apache.ace.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.SortedSet;

import org.osgi.framework.Version;

/**
 * Agent context delegate interface that is responsible for managing agent updates.
 */
public interface AgentUpdateHandler {

    /** Returns the locally installed version of the agent. */
    Version getInstalledVersion();

    /** Returns the versions available on the server. */
    SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException;

    /** Returns an input stream for the update of the agent. */
    InputStream getInputStream(Version version) throws RetryAfterException, IOException;

    /** Returns a download handle to download the update of the agent. */
    DownloadHandle getDownloadHandle(Version version) throws RetryAfterException, IOException;

    /** Returns the size of the update of the agent. */
    long getSize(Version version) throws RetryAfterException, IOException;

    /** Installs the update of the agent. */
    void install(InputStream stream) throws IOException;
}
