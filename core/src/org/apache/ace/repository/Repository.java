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

/**
 * Providing full access to a repository, which includes read and write access.
 */
public interface Repository {

    /**
     * Determines the versions inside the repository.
     * 
     * @returns A <code>SortedRangeSet</code> representing all the versions currently inside the repository.
     * @throws IOException If there is an error determining the current versions.
     */
    public SortedRangeSet getRange() throws IOException;
    
    /**
     * Commits data into the repository.
     *
     * @param data The data to be committed.
     * @param fromVersion The version the data is based upon.
     * @return True if the commit succeeded, false otherwise if the <code>fromVersion</code> is not the latest version.
     * @throws IOException If there was a problem reading or writing the data.
     * @throws IllegalArgumentException If the version is not greater than 0.
     * @throws IllegalStateException If an attempt to commit was made on a non-master repository.
     */
    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException;

    /**
     * Checks out the version of the repository that have been passed to this
     * method as parameter.
     * @return a stream containing a checkout of the passed in version of
     * the repository, or null if the version does not exist
     * @throws IOException if there is an error reading the version
     * @throws IllegalArgumentException if the version is invalid.
     */
    public InputStream checkout(long version) throws IOException, IllegalArgumentException;
}
