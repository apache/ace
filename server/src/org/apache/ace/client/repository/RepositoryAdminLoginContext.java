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
package org.apache.ace.client.repository;

import java.net.URL;

/**
 * RepositoryAdminLoginContext represents a context for logins to the repository admin. It is used to specify
 * which repositories are to be used in a given session with the RepositoryAdmin.
 */
public interface RepositoryAdminLoginContext {

    /**
     * Adds a set of repositories and their location to this login context.
     * @param repositoryLocation The location of the repository where this set's data resides.
     * @param repositoryCustomer The customer name for the location of the repository where this set's data resides.
     * @param repositoryName The repository name for the location of the repository where this set's data resides.
     * @param writeAccess Whether or not we need write access to this location. If <code>false</code>, readonly access
     * will be used.
     * @param objectRepositories The interfaces classes of the repositories to be used for this set.
     * @return this object, to allow chaining.
     */
    @SuppressWarnings("unchecked")
    public RepositoryAdminLoginContext addRepositories(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess, Class<? extends ObjectRepository>... objectRepositories);

    /**
     * Adds a shop repository to this login context.
     * @param repositoryLocation The location of the repository where this set's data resides.
     * @param repositoryCustomer The customer name for the location of the repository where this set's data resides.
     * @param repositoryName The repository name for the location of the repository where this set's data resides.
     * @param writeAccess Whether or not we need write access to this location. If <code>false</code>, readonly access
     * will be used.
     * @return this object, to allow chaining.
     */
    public RepositoryAdminLoginContext addShopRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess);

    /**
     * When uploads are needed, this is the base OBR that will be used.
     * @param base The URL of the OBR to be used.
     * @return this object, to allow chaining.
     */
    public RepositoryAdminLoginContext setObrBase(URL base);

    /**
     * Adds a gateway repository to this login context.
     * @param repositoryLocation The location of the repository where this set's data resides.
     * @param repositoryCustomer The customer name for the location of the repository where this set's data resides.
     * @param repositoryName The repository name for the location of the repository where this set's data resides.
     * @param writeAccess Whether or not we need write access to this location. If <code>false</code>, readonly access
     * will be used.
     * @return this object, to allow chaining.
     */
    public RepositoryAdminLoginContext addGatewayRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess);

    /**
     * Adds a deployment repository to this login context.
     * @param repositoryLocation The location of the repository where this set's data resides.
     * @param repositoryCustomer The customer name for the location of the repository where this set's data resides.
     * @param repositoryName The repository name for the location of the repository where this set's data resides.
     * @param writeAccess Whether or not we need write access to this location. If <code>false</code>, readonly access
     * will be used.
     * @return this object, to allow chaining.
     */
    public RepositoryAdminLoginContext addDeploymentRepository(URL repositoryLocation, String repositoryCustomer, String repositoryName, boolean writeAccess);
}
