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

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;

/**
 * Interface to a ArtifactRepository. The functionality is defined by the generic AssociationRepository.
 */
public interface ArtifactRepository extends ObjectRepository<ArtifactObject>{
	/**
	 * Gets a list of all ArtifactObject's which are resource processing bundles.
	 */
	public List<ArtifactObject> getResourceProcessors();

	/**
	 * Tries to import an artifact into storage, while extracting necessary metadata.
	 * @param artifact a URL pointing to the 'physical' artifact.
	 * @param upload Indicates whether this artifact should be uploaded to our own OBR.
	 * @return An <code>ArtifactObject</code> representing the passed in artifact, if
	 * (a) the artifact is recognized, (b) there is storage available and (c) there is
	 * a resource processor available for this type of artifact.
	 * @throws IllegalArgumentException when the <code>artifact</code> cannot be processed.
	 * @throws IOException when there is a problem transferring the <code>artifact</code> to storage.
	 */
	public ArtifactObject importArtifact(URL artifact, boolean upload) throws IllegalArgumentException, IOException;

	/**
	 * Checks whether an artifact is 'usable', that is, there is a resource processor available for it,
	 * if necessary.
	 * @param artifact A URL pointing to an artifact.
	 * @return <code>true</code> if the artifact is recognized, and a processor for it is available. <code>false</code>
	 * otherwise, including when the artifact cannot be reached.
	 */
	public boolean recognizeArtifact(URL artifact);

    /**
     * Tries to import an artifact into storage, while extracting necessary metadata.
     * @param artifact a URL pointing to the 'physical' artifact.
     * @param mimetype a String giving this object's mimetype.
     * @param upload Indicates whether this artifact should be uploaded to our own OBR.
     * @return An <code>ArtifactObject</code> representing the passed in artifact, if
     * (a) there is storage available and (b) there is a resource processor
     * available for this type of artifact.
     * @throws IllegalArgumentException when the <code>artifact</code> cannot be processed.
     * @throws IOException when there is a problem transferring the <code>artifact</code> to storage.
     */
	public ArtifactObject importArtifact(URL artifact, String mimetype, boolean upload) throws IllegalArgumentException, IOException;

	/**
	 * Tries to locate a preprocessor for the passed artifact, an processes it. If no processing
	 * needs to be done, the original artifact's URL will be returned.
	 * @param artifact An artifact
	 * @param props A tree of properties objects, to be used for replacement.
	 * @param gatewayID The gatewayID of the gateway for which this artifact is being processed.
	 * @param version The deployment version for which this artifact is being processed.
	 * @return A URL to a new, processed artifact, or to the original one, in case nothing needed to be processed.
     * @throws IOException Thrown if reading the original artifact goes wrong, or storing the processed one.
	 */
	public String preprocessArtifact(ArtifactObject artifact, GatewayObject gateway, String gatewayID, String version) throws IOException ;

    /**
     * Indicates whether the template should be processed again, given the properties, and the version to which it
     * should be compared.
     * @param url A string representing a URL to the original artifact.
     * @param props A PropertyResolver which can be used to fill in 'holes' in the template.
     * @param gatewayID The gatewayID of the gateway for which this artifact is being processed.
     * @param version The deployment version for which this artifact is being processed.
     * @param lastVersion The deployment version to which the current one should be compared.
     * @param newVersion The new, potential version.
     * @param obrBase A base OBR to upload the new artifact to.
     * @return Whether or not a new version has to be created.
     * @throws IOException
     */
    public boolean needsNewVersion(ArtifactObject artifact, GatewayObject gateway, String gatewayID, String fromVersion);

	/**
	 * Sets the OBR that this artifact repository should use to upload artifacts to.
	 */
	public void setObrBase(URL obrBase);

    /**
     * Gets the OBR that this artifact repository should use to upload artifacts to.
     * Note that this method may return <code>null</code> if no base was set earlier.
     */
    public URL getObrBase();
}
