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

package org.apache.ace.client.repository.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Denotes a 'physical' artifact that is located by an URL, and provide means to access the contents of this artifact.
 * <p>
 * Note that an artifact can be located on a remote machine, which might need credentials to access its contents. This
 * interface allows one to access the resource without having to worry about supplying those credentials, as the
 * implementor of this class has to worry about this instead.
 * </p>
 */
@ProviderType
public interface ArtifactResource {

    /**
     * Returns the location of this artifact.
     * <p>
     * Note that although {@link URL#openConnection()} allows you to directly open a connection to the resource, in fact
     * this may fail due to, for example, missing authentication credentials. Use {@link #openStream()} instead to
     * access the contents of the resource.
     * </p>
     *
     * @return the URL to the 'physical' location of the artifact, never <code>null</code>.
     */
    URL getURL();

    /**
     * Returns the size, in bytes, of this artifact.
     *
     * @return a size, in bytes, >= 0L. If the size of this artifact is unknown, <tt>-1L</tt> should be returned.
     * @throws IOException
     *             in case of I/O errors determining the size of the artifact.
     */
    long getSize() throws IOException;

    /**
     * Provides access to the contents of the artifact.
     *
     * @return an input stream, never <code>null</code>.
     * @throws IOException
     *             in case of I/O errors opening the artifact.
     * @see #getURL()
     */
    InputStream openStream() throws IOException;
}
