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

import java.util.Comparator;
import java.util.Map;
import org.apache.ace.client.repository.object.ArtifactObject;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Interface to an artifact helper. For each type of artifact, there should be a helper
 * service implementing this interface. The service should be registered with the mimetype
 * in the service's properties, so it can be identified. The <code>KEY_MIMETYPE</code> in
 * this class can be used for this purpose.
 */
@ConsumerType
public interface ArtifactHelper
{
    public static final String KEY_MIMETYPE = "mimetype";

    /**
     * Checks whether this helper can 'do anything' with this artifact object.
     * @param object An artifact object.
     * @return <code>true</code> when this helper can use the object, <code>false</code> otherwise.
     */
    public boolean canUse(ArtifactObject object);

    /**
     * Returns the artifact preprocessor that is associated with the type of artifact this
     * helper helps. Return null when no useful processor is available.
     * @return An artifact preprocessor, or <code>null</code> if no useful preprocessor can be created.
     */
    public ArtifactPreprocessor getPreprocessor();

    /**
     * Creates a filter string for use in associations, optionally with some
     * additional properties. The basic implementation will use all <code>getDefiningKeys</code>.
     * @param properties Properties indicating specifics of the filter to be created.
     * @return A string representation of a filter, for use in <code>Association</code>s.
     */
    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties);

    /**
     * Determines the cardinality of this endpoint of an association, given
     * the passed properties.
     * @param properties Properties indicating specifics of this endpoint.
     * @return The necessary cardinality.
     */
    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties);

    /**
     * Returns a <code>Comparator</code> for this type of object. Descendent
     * classes are expected to return a comparator if they can be meaningfully compared,
     * and otherwise (if no order is natural), return <code>null</code>.
     * @return A <code>Comparator</code> for this type of object
     */
    public Comparator<ArtifactObject> getComparator();

    /**
     * Checks the correctness of the given attributes for this type of object. If they
     * are correct, the map will be returned, potentially with some changes, and if not,
     * an {@link IllegalArgumentException} will be raised. Optionally, this
     * function can do some validation of input parameters, such as normalizing numbers.
     */
    public Map<String, String> checkAttributes(Map<String, String> attributes);

    /**
     * Gets an array of keys in the attributes that are considered defining for this type
     * of object; the combination of values of these keys should result in a unique
     * identification of the object.
     */
    public String[] getDefiningKeys();

    /**
     * Gets an array of all attributes that have to be present when creating an object
     * of this type.
     */
    public String[] getMandatoryAttributes();
}