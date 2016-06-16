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

import java.util.Map;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Service interface for services that can recognize the type of an artifact, given a URL
 * to that artifact.
 */
@ConsumerType
public interface ArtifactRecognizer
{
    /**
     * Tries to determine the type of the artifact. If this recognizer cannot determine the type, it
     * should return <code>null</code>.
     *
     * @param artifact the artifact to recognize, cannot be <code>null</code>.
     * @return The mimetype of the artifact, or <code>null</code> if the artifact is not recognized.
     */
    public String recognize(ArtifactResource artifact);

    /**
     * Gets the relevant metadata for this artifact.
     *
     * @param artifact the artifact to extract the metadata for, cannot be <code>null</code>.
     * @return A map of strings, representing the relevant metadata specific for this artifact. The
     *         keys are best defined in the corresponding <code>ArtifactHelper</code> interface for this type of artifact.
     *         This function should also set the <code>ArtifactObject.KEY_PROCESSOR_PID</code> attribute.<br>
     *         Optionally, <code>ArtifactObject.KEY_ARTIFACT_NAME</code> and <code>ArtifactObject.KEY_ARTIFACT_DESCRIPTION</code>
     *         can be set.
     * @throws IllegalArgumentException when the metadata cannot be retrieved from the <code>artifact</code>.
     */
    public Map<String, String> extractMetaData(ArtifactResource artifact) throws IllegalArgumentException;

    /**
     * Indicates whether this recognizer can handle (i.e., extract metadata) from an artifact of
     * a given mime type.
     *
     * @param mimetype The mimetype of an artifact.
     * @return <code>true</code> when this type should be able to be handled by this recognizer;
     *         <code>false</code> otherwise.
     */
    public boolean canHandle(String mimetype);

    /**
     * Returns a preferred extension for the file name if a new one is created.
     *
     * @param artifact the artifact to get the extension for, cannot be <code>null</code>.
     *
     * @return The extension that is preferred or an empty string if there is none.
     */
    public String getExtension(ArtifactResource artifact);
}