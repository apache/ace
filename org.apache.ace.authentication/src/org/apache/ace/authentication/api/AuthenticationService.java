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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a generic and pluggable authentication service.
 * <p>
 * This service provides the front end to all services that wish to authenticate a user. In short,
 * this service will look up all available {@link AuthenticationProcessor}s and use them to perform
 * the actual authentication.
 * </p>
 * 
 * @see AuthenticationProcessor
 */
@ProviderType
public interface AuthenticationService {

    /**
     * Authenticates a user based on the given context information.
     * <p>
     * The context information can be any kind of object, hence it is not exactly typed. As this
     * service is pluggable, it is up to the authentication processors to interpret the context
     * information.
     * </p>
     * <p>
     * Implementations can decide on the strategy of authentication, whether all participating
     * authentication processors <b>must</b> or <b>may</b> match.<br/>
     * If multiple authentication processors are found, they <b>must</b> be ordered on their 
     * <em>service.ranking</em> property. The one with the higest service ranking is used first,
     * and so on.
     * </p>
     * 
     * @param context the context information, cannot be <code>null</code> or an empty array.
     * @return an authenticated {@link User}, or <code>null</code> if authentication failed
     *         (or otherwise was not possible).
     * @throws IllegalArgumentException in case the given context was <code>null</code> or an empty array;
     * @throws NullPointerException in case the given array contains <code>null</code> as element(s).
     */
    User authenticate(Object... context);
}
