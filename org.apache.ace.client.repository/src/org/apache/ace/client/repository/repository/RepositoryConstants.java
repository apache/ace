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

package org.apache.ace.client.repository.repository;

import java.net.URL;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Constants used for the repository.
 */
@ProviderType
public interface RepositoryConstants {
    /**
     * Configuration key for hiding or showing unregistered targets. The value should either be a {@link Boolean} or a
     * {@link String} containing <tt>true</tt> (= the default value) or <tt>false</tt>.
     */
    String KEY_SHOW_UNREGISTERED_TARGETS = "showunregisteredtargets";

    /**
     * Configuration key for limiting the number of deployment versions per target. In case the number of deployment
     * versions for a target hits this limit, the oldest deployment versions will be purged for this target. The value
     * should be an {@link Integer} or a {@link String} representing the integer value. A value of <tt>-1</tt> (= the
     * default in case no value is supplied) means that no limit is imposed, and that <em>all</em> deployment versions
     * are retained for each target.
     */
    String KEY_DEPLOYMENT_VERSION_LIMITS = "deploymentversionlimit";

    /**
     * Configuration key for defining where the OBR is located to store artifacts in. The value should either be a
     * {@link URL} or a String representing a valid URL. Defaults to <tt>http://localhost:8080/obr/</tt>.
     */
    String KEY_OBR_LOCATION = "obrlocation";

}
