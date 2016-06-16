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
 * Providing full access to a repository, which includes read and write access.
 */
@ProviderType
public interface Repository
{
    /**
     * Determines the versions present inside the repository.
     * 
     * @returns A <code>SortedRangeSet</code> representing all the versions currently inside the repository, never
     *          <code>null</code>.
     * @throws java.io.IOException
     *             If there is an error determining the current versions.
     */
    public SortedRangeSet getRange() throws IOException;

    /**
     * Commits data into the repository.
     * 
     * @param data
     *            The input stream containing the data to be committed, cannot be <code>null</code>;
     * @param fromVersion
     *            The version the to-be-committed data is based upon, is used to verify no other commits have taken
     *            place between the last checkout and this commit.
     * @return <code>true</code> if the commit succeeded (and a new version is created in this repository),
     *         <code>false</code> if the commit was <em>ignored</em> because no actual changes in data were found.
     * @throws java.io.IOException
     *             If there was a problem reading or writing the data, or when trying to commit a version that is not
     *             the "current" version;
     * @throws IllegalArgumentException
     *             If the version is less than <tt>0</tt>;
     * @throws IllegalStateException
     *             If an attempt to commit was made on a non-master repository.
     */
    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException;

    /**
     * Checks out the version of the repository that have been passed to this method as parameter.
     * 
     * @return a stream containing a checkout of the given repository version, or <code>null</code> if the version does
     *         not exist.
     * @throws java.io.IOException
     *             if there is an error reading the requested version of the repository;
     * @throws IllegalArgumentException
     *             if the given version is less than or equal to <tt>0</tt>.
     */
    public InputStream checkout(long version) throws IOException, IllegalArgumentException;
}
