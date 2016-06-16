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

import org.osgi.annotation.versioning.ProviderType;

/**
 * RepositoryAdminLoginContext represents a context for logins to the repository admin. It is used to specify
 * which repositories are to be used in a given session with the RepositoryAdmin.
 */
@ProviderType
public interface RepositoryAdminLoginContext
{
    /**
     * Provides a common interface for all repository contexts.
     * 
     * @param <T> the actual context type to return in this builder.
     */
    public interface BaseRepositoryContext<T extends BaseRepositoryContext<?>> {
    
        /**
         * @param location the location of the repository where this set's data resides.
         * @return this context.
         */
        T setLocation(URL location);
    
        /**
         * @param customer the customer name for the location of the repository where this set's data resides.
         * @return this context.
         */
        T setCustomer(String customer);
    
        /**
         * @param name the repository name for the location of the repository where this set's data resides.
         * @return this context.
         */
        T setName(String name);
    
        /**
         * Marks this repository as writable (default is read-only).
         * 
         * @return this context.
         */
        T setWriteable();
    }

    /**
     * Denotes a context for creating shop repositories.
     */
    public static interface ShopRepositoryContext extends BaseRepositoryContext<ShopRepositoryContext> {
    }

    /**
     * Denotes a context for creating target repositories.
     */
    public static interface TargetRepositoryContext extends BaseRepositoryContext<TargetRepositoryContext> {
    }

    /**
     * Denotes a context for creating deployment repositories.
     */
    public static interface DeploymentRepositoryContext extends BaseRepositoryContext<DeploymentRepositoryContext> {
    }

    /**
     * @return a new shop repository context, never <code>null</code>.
     */
    public ShopRepositoryContext createShopRepositoryContext();

    /**
     * @return a new target repository context, never <code>null</code>.
     */
    public TargetRepositoryContext createTargetRepositoryContext();

    /**
     * @return a new deployment repository context, never <code>null</code>.
     */
    public DeploymentRepositoryContext createDeploymentRepositoryContext();

    /**
     * @param repositoryContext the context to add, cannot be <code>null</code>.
     * @return this context, never <code>null</code>.
     */
    public RepositoryAdminLoginContext add(BaseRepositoryContext<?> repositoryContext);
}
