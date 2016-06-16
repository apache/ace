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
package org.apache.ace.authentication.api;

import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Provides a pluggable authentication processor, responsible for the actual authentication of a
 * user based on given context information.
 * <p>
 * When multiple authentication processors are implemented and used for the authentication process,
 * an order in which they should be used is determined based on their <em>service ranking</em>.
 * </p>
 */
@ConsumerType
public interface AuthenticationProcessor {

    /**
     * Returns whether or not this authentication processor can handle the given context
     * information.
     * <p>
     * NOTE: this method does not need to perform the actual authentication!
     * </p>
     * <p>
     * For example, for an implementation that authenticates a user based on its username
     * and password might check whether the given context information consists of two
     * strings.
     * </p>
     *
     * @param context the context information to check, should never be <code>null</code> or an
     *        empty array.
     * @return <code>true</code> if this authentication processor can handle the given context
     *         information, <code>false</code> otherwise.
     * @throws IllegalArgumentException in case the given context was <code>null</code> or an empty array;
     * @throws NullPointerException in case the given array contains <code>null</code> as element(s).
     */
    boolean canHandle(Object... context);

    /**
     * Authenticates a user based on the given context information.
     *
     * @param userAdmin the user admin service, to use for verifying/retrieving user information,
     *        cannot be <code>null</code>;
     * @param context the context information to authenticate the user with, should never be
     *        <code>null</code> or an empty array.
     * @return the authenticated user, or <code>null</code> if authentication failed.
     * @throws IllegalArgumentException in case the given context was <code>null</code> or an empty array;
     * @throws NullPointerException in case the given array contains <code>null</code> as element(s).
     */
    User authenticate(UserAdmin userAdmin, Object... context);
}
