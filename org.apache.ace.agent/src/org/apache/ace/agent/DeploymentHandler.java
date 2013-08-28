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
import org.osgi.service.deploymentadmin.DeploymentException;

/**
 * Agent context delegate interface that provides the deployment functions.
 */
public interface DeploymentHandler {

    /**
     * Return the installed deployment package version for this agent.
     * 
     * @return The installed version, {@link Version.emptyVersion} if no packages have been installed
     */
    Version getInstalledVersion();

    /**
     * Return the sorted set of available deployment package versions as reported by the server.
     * 
     * @return The sorted set of versions, may be empty
     * @throws RetryAfterException If the server indicates it is too busy with a Retry-After header
     * @throws IOException If the connection to the server fails
     */
    SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException;

    /**
     * Return the estimated size for a deployment package as reported by the server.
     * 
     * @param version The version of the package
     * @param fixPackage Request the server for a fix-package
     * @return The estimated size in bytes, <code>-1</code> indicates the size is unknown
     * @throws RetryAfterException If the server indicates it is too busy with a Retry-After header
     * @throws IOException If the connection to the server fails
     */
    long getPackageSize(Version version, boolean fixPackage) throws RetryAfterException, IOException;

    /**
     * Returns the {@link InputStream} for a deployment package.
     * 
     * @param version The version of the deployment package
     * @param fixPackage Request the server for a fix-package
     * @return The input-stream for the deployment package
     * @throws RetryAfterException If the server indicates it is too busy with a Retry-After header
     * @throws IOException If the connection to the server fails
     */
    InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException;

    /**
     * Return the {@link DownloadHandle} for a deployment package.
     * 
     * @param version The version of the deployment package
     * @param fixPackage Request the server for a fix-package
     * @return The download handle
     */
    DownloadHandle getDownloadHandle(Version version, boolean fixPackage) throws RetryAfterException, IOException;

    /**
     * Install a deployment package from an input stream.
     * 
     * @param inputStream The inputStream, not <code>null</code>
     * @throws IOException If reading the input stream fails.
     */
    // TODO should we expose the foreign exception?
    void deployPackage(InputStream inputStream) throws DeploymentException, IOException;
}
