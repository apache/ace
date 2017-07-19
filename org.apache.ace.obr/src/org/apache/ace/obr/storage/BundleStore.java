package org.apache.ace.obr.storage;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.annotation.versioning.ProviderType;

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

@ProviderType
public interface BundleStore {

    /**
     * Returns whether or not a specified resource exists.
     *
     * @param filePath
     *            the relative path of the resource
     * @return <code>true</code> if the resource exists, <code>false</code> otherwise.
     * @throws IOException
     *             in case there was a problem testing the existance of the requested resource.
     */
    boolean exists(String filePath) throws IOException;

    /**
     * Returns an <code>InputStream</code> to the data of the specified resource.
     *
     * @param filePath
     *            Relative path of the resource.
     * @return <code>InputStream</code> to the requested resource or <code>null</code> if no such resource is available.
     * @throws java.io.IOException
     *             If there was a problem returning the requested resource.
     */
    InputStream get(String filePath) throws IOException;

    /**
     * Stores the specified resource in the store. If the resource already existed, it will only be accepted if you
     * either try to store exactly the same resource (byte-by-byte) or tell it to forcefully replace the resource. The
     * latter should only be done with extreme care.
     *
     * @param fileName
     *            name of the resource, ignored if the resource is an OSGi bundle
     * @param data
     *            the actual data of the resource
     * @param replace
     *            <code>true</code> to replace any existing resource with the same name
     * @return the filePath if the resource was successfully stored, <code>null</code> if the resource already existed
     *         and was different
     * @throws java.io.IOException
     *             If there was a problem reading or writing the data of the resource.
     */
    String put(InputStream data, String fileName, boolean replace) throws IOException;

    /**
     * Removes the specified resource from the store.
     *
     * @param filePath
     *            Relative path of the the resource.
     * @return <code>true</code> if the resource was successfully removed, <code>false</code> if the resource was not
     *         present to begin with
     * @throws java.io.IOException
     *             If there was a problem removing the data of the resource from the store.
     */
    boolean remove(String filePath) throws IOException;
}
