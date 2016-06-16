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
package org.apache.ace.repository.ext;

import java.io.IOException;
import java.io.InputStream;
import org.apache.ace.repository.Repository;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a cached repository representation, allowing the storing of local changes, without
 * committing them to the actual repository immediately.
 */
@ProviderType
public interface CachedRepository extends Repository {

    /**
     * Checks our the most current version from the actual repository.
     * @param fail Indicates that this method should throw an IOException when no data
     * is available. Setting it to <code>false</code> will make it return an
     * empty stream in that case.
     * @return An input stream representing the checked out object.
     * @throws java.io.IOException Is thrown when the actual repository's commit does.
     */
    public InputStream checkout(boolean fail) throws IOException;

    /**
     * Commits the most current version to the actual repository.
     * @return true on success, false on failure (e.g. bad version number)
     * @throws java.io.IOException Is thrown when the actual repository's commit does.
     */
    public boolean commit() throws IOException;

    /**
     * Gets the most recent version of the object. If no current version is available,
     * <code>null</code> will be returned.
     * @param fail Indicates that this method should throw an IOException when no data
     * is available. Setting it to <code>false</code> will make it return an
     * empty stream in that case.
     * @return An input stream representing the most recently written object.
     * @throws java.io.IOException Thrown when there is a problem retrieving the data.
     */
    public InputStream getLocal(boolean fail) throws IOException;

    /**
     * Writes the most recent version of the object.
     * @throws java.io.IOException Thrown when there is a problem storing the data.
     */
    public void writeLocal(InputStream data) throws IOException;

    /**
     * Undoes all changes made using <code>writeLocal()</code> since the
     * last <code>commit</code> or <code>checkout</code>.
     * @throws java.io.IOException
     */
    public boolean revert() throws IOException;

    /**
     * Gets the most recent version of this repository, that is, the most recent version
     * number that is either committed (successfully) or checked out.
     * @return The most recent version of the underlying repository.
     */
    public long getMostRecentVersion();

    /**
     * Checks whether the version we have locally is current with respect to the version
     * on the server.
     * @return whether the version we have locally is current with respect to the version
     * on the server.
     * @throws java.io.IOException Thrown when an error occurs communicating with the server.
     */
    public boolean isCurrent() throws IOException;

    /**
     * Deletes the local repository.
     *
     * @throws IOException when the local repository could not be deleted
     */
    public void deleteLocal() throws IOException;
}