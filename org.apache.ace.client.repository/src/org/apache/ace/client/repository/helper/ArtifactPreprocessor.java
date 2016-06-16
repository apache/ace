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

import java.io.IOException;
import java.net.URL;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * An ArtifactPreprocessor processes an artifact before it is deployed.
 */
@ConsumerType
public interface ArtifactPreprocessor {
    /**
     * Preprocesses a single artifact, uploads it to the obr, and returns the new URL as a string.
     *
     * @param url A string representing a URL to the original artifact.
     * @param props A PropertyResolver which can be used to fill in 'holes' in the template.
     * @param targetID The targetID of the target for which this artifact is being processed.
     * @param version The deployment version for which this artifact is being processed.
     * @param obrBase A base OBR to upload the new artifact to.
     * @return A URL to the new object (or the old one, if no replacing was necessary), as a string.
     * @throws java.io.IOException Thrown if reading the original artifact goes wrong, or storing the processed one.
     */
    public String preprocess(String url, PropertyResolver props, String targetID, String version, URL obrBase) throws IOException;

    /**
     * Indicates whether the template should be processed again, given the properties, and the version to which it
     * should be compared.
     *
     * @param url A string representing a URL to the original artifact.
     * @param props A PropertyResolver which can be used to fill in 'holes' in the template.
     * @param targetID The targetID of the target for which this artifact is being processed.
     * @param version The deployment version for which this artifact is being processed.
     * @param fromVersion The deployment version to which the current one should be compared.
     * @return <code>false</code> if the version of the processed artifact identified by <code>fromVersion</code>
     *         is identical to what would be created using the new <code>props</code>; <code>true</code> otherwise.
     * @throws java.io.IOException
     */
    public boolean needsNewVersion(String url, PropertyResolver props, String targetID, String fromVersion);
}
