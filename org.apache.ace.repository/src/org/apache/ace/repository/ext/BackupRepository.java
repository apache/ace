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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides an interface for backing up objects. <code>write</code> and <code>read</code>
 * allow writing and reading of the current version of the object. <code>backup</code>
 * backs up the object, and <code>restore</code> restores it from a previously backed up
 * version, if any. There is no way to directly use the backup.
 */
@ProviderType
public interface BackupRepository
{

    /**
     * Writes the input stream to the current object.
     * @param data The data to be written. Remember to close this stream, if necessary.
     * @throws java.io.IOException Will be thrown when (a) the input stream gets closed
     * unexpectedly, or (b) there is an error writing the data.
     */
    public void write(InputStream data) throws IOException;

    /**
     * Reads the input stream from the current object. If there is no current version,
     * <code>null</code> will be returned.
     * @return An input stream, from which can be read. Remember to close it.
     * @throws java.io.IOException Will be thrown when there is a problem storing the data.
     */
    public InputStream read() throws IOException;

    /**
     * Restores a previously backuped version of the object.
     * @return True when there was a previously backup version which has
     * now been restored, false otherwise.
     * @throws java.io.IOException Thrown when the restore process goes bad.
     */
    public boolean restore() throws IOException;

    /**
     * Backs up the current version of the object, overwriting a previous
     * backup, if any.
     * @return True when there was a current version to be backed up, false
     * otherwise.
     * @throws java.io.IOException Thrown when the restore process goes bad.
     */
    public boolean backup() throws IOException;

    /**
     * Deletes the whole repository.
     *
     * @throws IOException when the repository could not be deleted.
     */
    public void delete() throws IOException;
}