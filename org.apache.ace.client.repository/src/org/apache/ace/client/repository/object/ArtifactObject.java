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
package org.apache.ace.client.repository.object;

import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface to an ArtifactObject. The basic functionality is defined by RepositoryObject, but extended for
 * artifact-specific information.
 */
@ProviderType
public interface ArtifactObject extends RepositoryObject {
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Indicates the location of the persistent storage of the artifact.
     */
    public static final String KEY_URL = "url";
    /**
     * Attribute key, stating the unique name for this resource. Different versions of the same logical resource
     * can share the same Id.
     */
    public static final String KEY_RESOURCE_ID = "resourceId";
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Indicates the PID of the resource processor that should be used to process this artifact.
     * For a bundle, it is empty.
     */
    public static final String KEY_PROCESSOR_PID = "processorPid";
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Indicates the mimetype of this artifact. For artifacts which do not
     * have an adequately discriminating mimetype, it can be extended with
     * something non-standard.
     */
    public static final String KEY_MIMETYPE = "mimetype";
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Holds a human-readable name for this artifact.
     */
    public static final String KEY_ARTIFACT_NAME = "artifactName";
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Holds a human-readable description for this artifact.
     */
    public static final String KEY_ARTIFACT_DESCRIPTION = "artifactDescription";
    /**
     * Key to be used in the <code>ArtifactObject</code>'s attributes.
     * Holds the (estimated) size, in bytes, for this artifact.
     */
    public static final String KEY_SIZE = "artifactSize";

    public static final String TOPIC_ENTITY_ROOT = ArtifactObject.class.getSimpleName() + "/";

    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    public static final String PRIVATE_TOPIC_ADDED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String PRIVATE_TOPIC_REMOVED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String PRIVATE_TOPIC_CHANGED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String PRIVATE_TOPIC_ALL = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Returns all <code>GroupObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<FeatureObject> getFeatures();
    /**
     * Returns all associations this artifact has with a given group.
     */
    public List<Artifact2FeatureAssociation> getAssociationsWith(FeatureObject group);

    /**
     * Returns the mimetype of this artifact.
     */
    public String getMimetype();
    /**
     * Returns the PID of the resource processor of this artifact.
     */
    public String getProcessorPID();
    /**
     * Sets the PID of the resource processor of this artifact.
     */
    public void setProcessorPID(String processorPID);
    /**
     * Returns the URL to this artifact.
     */
    public String getURL();
    /**
     * Returns a ResourceId, if that has been customized.
     */
    public String getResourceId();
    /**
     * Return a descriptive name for this object. May return <code>null</code>.
     */
    public String getName();
    /**
     * Returns a description for this object. May return <code>null</code>.
     */
    public String getDescription();
    /**
     * Returns an (estimated) size, in bytes, for this object, can be -1L if no size is known.
     */
    public long getSize();
    /**
     * Sets a description for this artifact.
     */
    public void setDescription(String value);
}
