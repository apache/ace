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
package org.apache.ace.deployment;

import java.io.InputStream;

import org.osgi.framework.Version;

/**
 * Service that abstracts the actual implementation that manages components that are to be deployed.
 * Implementations of this interface could for example make use of the <code>DeploymentAdmin</code>
 * from the OSGI mobile spec to actually deploy packages. The objects used as arguments and return values
 * must all be of the same type, which type depends on the implementation.
 */
public interface Deployment {

    /**
     * Deploys the contents of the stream onto the system
     *
     * @param inputStream Stream containing new components.
     * @return The update package that was installed, may be null if the implementation does not support this.
     * @throws Exception If the specified stream could not be deployed.
     */
    public Object install(InputStream inputStream) throws Exception;

    /**
     * Gets the name of the specified update package. Guaranteed to work with <code>Object</code>s returned
     * by the same implementation of this interface.
     *
     * @param object The update package
     * @return the name
     * @throws IllegalArgumentException when the specified object is an invalid update package, only Objects returned by the same implementation of this interface should be used.
     */
    public String getName(Object object) throws IllegalArgumentException;

    /**
     * Gets the version of the specified update package. Guaranteed to work with <code>Object</code>s returned
     * by the same implementation of this interface.
     *
     * @param object The update package
     * @return the version
     * @throws IllegalArgumentException when the specified object is an invalid update package, only Objects returned by the same implementation of this interface should be used.
     */
    public Version getVersion(Object object) throws IllegalArgumentException;

    /**
     * Retrieve a list of installed update packages.
     *
     * @return list of installed update packages or an empty array if none are available.
     */
    public Object[] list();

}