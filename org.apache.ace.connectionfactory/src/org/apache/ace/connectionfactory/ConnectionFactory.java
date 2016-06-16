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
package org.apache.ace.connectionfactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.osgi.service.useradmin.User;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a service to create {@link URLConnection}s. The connection factory will be responsible
 * for supplying the necessary credentials to ensure the authentication of the connection succeeds.
 */
@ProviderType
public interface ConnectionFactory {

    /**
     * Creates a new connection using the given URL, using the (optional) credentials.
     *
     * @param url the URL to connect to, cannot be <code>null</code>.
     * @return a {@link URLConnection} instance, never <code>null</code>.
     * @throws IllegalArgumentException in case the given URL was <code>null</code>;
     * @throws IOException in case the creation of the connection failed.
     */
    URLConnection createConnection(URL url) throws IOException;

    /**
     * Creates a new connection using the given URL, using the (optional) credentials.
     *
     * @param url the URL to connect to, cannot be <code>null</code>;
     * @param user the user to fetch the credentials from, cannot be <code>null</code>.
     * @return a {@link URLConnection} instance, never <code>null</code>.
     * @throws IllegalArgumentException in case the given URL was <code>null</code>, or when the supplied credentials are missing information;
     * @throws IOException in case the creation of the connection failed.
     */
    URLConnection createConnection(URL url, User user) throws IOException;
}
