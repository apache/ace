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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents a basic repository of associations. The associations are to be of type
 * <code>T</code>, associating types <code>L</code> and <code>R</code>.
 */
@ProviderType
public interface AssociationRepository<L extends Associatable, R extends Associatable, T extends Association<L, R>> extends ObjectRepository<T>{
    /**
     * Creates a static association between two filters for objects, stores it,
     * and returns the association object. This association will link all objects
     * that apply to the filters in an m-to-n fashion.
     */
    public T create(String left, String right);
    /**
     * Creates a static association between two objects, stores it,
     * and returns the association object.
     */
    public T create(L left, R right);
    /**
     * Creates an association between the given objects, with the <code>Props</code> containing
     * additional directives for the endpoints, stores it, and returns the association.
     */
    public T create(L left, Map<String, String> leftProps, R right, Map<String, String> rightProps);
    /**
     * Creates a static association between two lists of objects, stores it,
     * and returns the association object.
     */
    public T create(List<L> left, List<R> right);
    /**
     * Removes the given association, and deletes the association from the left- and right
     * side of the association.
     */
    public void remove(T entity);
}