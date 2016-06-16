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

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Generic interface for installing updates.
 */
@ConsumerType
public interface UpdateHandler {
    /**
     * @return a short descriptive name for this update handler, for example, "agent updater".
     */
    String getName();

    /**
     * Return the sorted set of available update-versions as reported by the server.
     * 
     * @return a sorted set of versions, may be empty, but never be <code>null</code>.
     * @throws RetryAfterException
     *             if the server indicates it is too busy, and this call should be retried on a later moment;
     * @throws IOException
     *             in case the connection to the server failed.
     */
    SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException;

    /**
     * Return the {@link DownloadHandle} for an update.
     * 
     * @param version
     *            the version of the update to get a download handle for, cannot be <code>null</code>;
     * @param fixPackage
     *            <code>true</code> if a download handler for a fix-package should be requested, <code>false</code>
     *            otherwise.
     * @return a download handle for the requested update, never <code>null</code>.
     * @throws RetryAfterException
     *             if the server indicates it is too busy, and this call should be retried on a later moment.
     */
    DownloadHandle getDownloadHandle(Version version, boolean fixPackage) throws RetryAfterException;

    /**
     * Returns the highest available update-version as reported by the server.
     * 
     * @return the highest available version, never <code>null</code>, can be {@link Version#emptyVersion} in case no
     *         version is available.
     * @throws RetryAfterException
     *             if the server indicates it is too busy, and this call should be retried on a later moment;
     * @throws IOException
     *             in case the connection to the server failed.
     */
    Version getHighestAvailableVersion() throws RetryAfterException, IOException;

    /**
     * Returns the {@link InputStream} for an update.
     * 
     * @param version
     *            the version of the update to get an input-stream for, cannot be <code>null</code>;
     * @param fixPackage
     *            <code>true</code> if an input-stream for a fix-package should be requested, <code>false</code>
     *            otherwise.
     * @return the input-stream for the update, never <code>null</code>.
     * @throws RetryAfterException
     *             if the server indicates it is too busy, and this call should be retried on a later moment;
     * @throws IOException
     *             in case the connection to the server failed.
     */
    InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException;

    /**
     * Return version of the current installed update for this agent.
     * 
     * @return the installed version, {@link Version.emptyVersion} if no packages have been installed, never
     *         <code>null</code>.
     */
    Version getInstalledVersion();

    /**
     * Return the estimated size for an update as reported by the server.
     * 
     * @param version
     *            the version of the update to get a size estimation for, cannot be <code>null</code>;
     * @param fixPackage
     *            <code>true</code> if a size estimation for a fix-package should be requested, <code>false</code>
     *            otherwise.
     * @return the estimated size in bytes, <code>-1</code> indicates the size is unknown.
     * @throws RetryAfterException
     *             if the server indicates it is too busy, and this call should be retried on a later moment;
     * @throws IOException
     *             in case the connection to the server failed.
     */
    long getSize(Version version, boolean fixPackage) throws RetryAfterException, IOException;

    /**
     * Install an update from an input stream.
     * 
     * @param inputStream
     *            the inputStream, can not be <code>null</code>.
     * @throws InstallationFailedException
     *             in case the installation failed;
     * @throws IOException
     *             if reading from the given input stream fails.
     */
    void install(InputStream inputStream) throws InstallationFailedException, IOException;

}
