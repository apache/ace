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
package org.apache.ace.deployment.streamgenerator;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.deployment.provider.OverloadedException;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface StreamGenerator
{

    /**
     * Returns an input stream with the requested deployment package.
     *
     * @param id the ID of the package
     * @param version the version of the package
     * @return an input stream
     * @throws java.io.IOException when the stream could not be generated
     * @throws OverloadedException if the streamgenerator is overloaded
     */
    public InputStream getDeploymentPackage(String id, String version) throws OverloadedException, IOException;

    /**
     * Returns an input stream with the requested deployment fix package.
     *
     * @param id the ID of the package.
     * @param fromVersion the version of the target.
     * @param toVersion the version the target should be in after applying the package.
     * @return an input stream.
     * @throws java.io.IOException when the stream could not be generated.
     * @throws OverloadedException if the streamgenerator is overloaded
     */
    public InputStream getDeploymentPackage(String id, String fromVersion, String toVersion) throws OverloadedException, IOException;
}