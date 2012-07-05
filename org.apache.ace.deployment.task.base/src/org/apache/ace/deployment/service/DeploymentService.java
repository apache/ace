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
package org.apache.ace.deployment.service;

import java.io.IOException;
import java.util.SortedSet;

import org.osgi.framework.Version;

/**
 * Deployment service can be used to talk to the management agent about deployment packages,
 * versions and updates, and to actually perform them. This interface coexists with the
 * tasks that are also published by the management agent and that are probably more convenient
 * if you just want to schedule (checks for) updates.
 */
public interface DeploymentService {

    /**
     * Returns the highest version that is available locally (already installed).
     * 
     * @return The highest installed version, can be <code>null</code> if no version is locally available.
     */
    Version getHighestLocalVersion();

    /**
     * Returns the highest version that is available remotely.
     * 
     * @param url The URL to be used to retrieve the versions available on the remote.
     * @return The highest version available on the remote or <code>null</code> if no versions were available or the remote could not be reached.
     * @throws IOException in case of I/O problems obtaining the remote version.
     */
    Version getHighestRemoteVersion() throws IOException;

    /**
     * Returns all versions that are available remotely.
     * 
     * @return the remote versions, sorted, can be <code>null</code>.
     * @throws IOException in case of I/O problems obtaining the remote versions.
     */
    SortedSet<Version> getRemoteVersions() throws IOException;

    /**
     * Installs the version specified by the highestRemoteVersion.
     * 
     * @param remoteVersion the version to retrieve and install;
     * @param localVersion the current (local) version, can be <code>null</code> in case of no version is yet installed.
     * @throws IOException in case of I/O problems installing the version;
     * @throws Exception in case of other problems installing the version.
     */
    void installVersion(Version remoteVersion, Version localVersion) throws IOException, Exception;

    /**
     * Updates from the current local version to the given remote version.
     * <p>
     * This method is the same as calling:
     * <pre>
     * installVersion(toVersion, getHighestLocalVersion());
     * </pre>
     * </p>
     * 
     * @param toVersion the (remote) version to update to, cannot be <code>null</code>.
     * @throws Exception in case of other problems updating to the requested version.
     */
    void update(Version toVersion) throws Exception;

}
