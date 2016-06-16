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

import org.osgi.annotation.versioning.ProviderType;

/**
 * The Associatable interface is to be implemented by any object that wants to be used in an Association.
 */
@ProviderType
public interface Associatable
{
    /**
     * Adds the association to this object, which has the given class. The 'other side' of the association will now show
     * up when filtering for <code>clazz</code>, which is the class of the other end of the association.
     */
    public <T extends Associatable> void add(Association association, Class<T> clazz);

    /**
     * Removes the association from this object, with the given class. The 'other side' of the association will no
     * longer show up when filtering for <code>clazz</code>.
     */
    public <T extends Associatable> void remove(Association association, Class<T> clazz);

    /**
     * Gets all Associatable objects of the <code>clazz</code> with which this object is associated. If
     * <code>clazz</code> is not in use, this function will return an empty list.
     */
    public <T extends Associatable> List<T> getAssociations(Class<T> clazz);

    /**
     * Checks whether this object is related with <code>obj</code>, which is to be of class <code>clazz</code>. Will
     * also return <code>false</code> when the class does not match.
     */
    public <T extends Associatable> boolean isAssociated(Object obj, Class<T> clazz);

    /**
     * Returns the associations that exist between this object and the other, of the given <code>clazz</code>, in a
     * typed list of associations <code>associationType</code>. Will return an empty list if no associations exist.
     */
    public <T extends Associatable, A extends Association> List<A> getAssociationsWith(Associatable other, Class<T> clazz, Class<A> associationType);
}
