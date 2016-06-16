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

import java.util.List;
import java.util.Map;
import org.osgi.framework.Filter;

import org.osgi.annotation.versioning.ProviderType;

/**
 * A basic object repository, storing implementers of RepositoryObject.
 */
@ProviderType
public interface ObjectRepository<T extends RepositoryObject> {
    /**
     * Removes the given entity from this repository. Will silently fail
     * when the entity does not exist in this repository.
     */
    public void remove(T entity);
    /**
     * Gets a list of all entities in this repository.
     */
    public List<T> get();
    /**
     * Returns a list of all entities in this repository that satisfy
     * the conditions set in <code>filter</code>. If none match, an
     * empty list will be returned.
     */
    public List<T> get(Filter filter);
    /**
     * Returns the entity in this repository that has the given definition.
     * If none match, null will return. 
     * 
     * @param definition the definition of the entity to be returned
     * @return The entity in this repository that has the given definition or <code>null</code> if none.
     */
    public T get(String definition);

    /**
     * Creates a new inhabitant based on the given attributes. The object
     * will be stored in this repository's store, and will be returned.
     * @throws IllegalArgumentException Will be thrown when the attributes cannot be accepted.
     */
    public T create(Map<String, String> attributes, Map<String, String> tags) throws IllegalArgumentException;
}