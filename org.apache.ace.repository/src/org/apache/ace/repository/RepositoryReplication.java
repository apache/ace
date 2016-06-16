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
package org.apache.ace.repository;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.range.SortedRangeSet;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The interface for replication of the data in a repository.
 */
@ProviderType
public interface RepositoryReplication
{
    /**
     * Determines the versions inside the repository.
     * 
     * @returns A <code>SortedRangeSet</code> representing all the versions currently inside the repository.
     * @throws java.io.IOException If there is an error determining the current versions.
     */
    public SortedRangeSet getRange() throws IOException;

    /**
     * Gets the specified version.
     * 
     * @return A stream containing the specified version's data or <code>null</code> if the version does not exist.
     * @throws java.io.IOException If there is an error reading the version.
     * @throws IllegalArgumentException If the specified version is not greater than 0.
     */
    public InputStream get(long version) throws IOException, IllegalArgumentException;

    /**
     * Store the stream data as the specified version.
     * 
     * @return returns True if all went fine, false if the version already existed.
     * @throws java.io.IOException If the stream data could not be stored successfully due to I/O problems.
     * @throws IllegalArgumentException If the version number is not greater than 0.
     */
    public boolean put(InputStream data, long version) throws IOException, IllegalArgumentException;
    
    /**
     * Returns the maximum number of versions this repository will store.
     */
    public long getLimit();
}