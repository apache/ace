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

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Interface for resolving properties about the template's
 * environment which are to be used by an ArtifactPreprocessor.
 */
@ConsumerType
public interface PropertyResolver
{
    /**
     * Gets a property, based on the given key. If the key cannot be found, <code>null</code>
     * can be used.
     * @param key A key to some property. Cannot be null.
     * @return The property identified by <code>key</code> if it can be found, <code>null</code> otherwise.
     */
    public String get(String key);
}