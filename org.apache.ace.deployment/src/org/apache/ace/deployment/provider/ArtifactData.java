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
package org.apache.ace.deployment.provider;

import java.net.URL;
import java.util.jar.Attributes;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The ArtifactData as returned by the <code>DeploymentProvider</code> class in this package. It contains several pieces
 * of data which describe the artifact and the place where it can be found.
 */
@ProviderType
public interface ArtifactData {

    /**
     * Indicate if the bundle has changed. This is used when comparing artifacts in 2 versions. (see DeploymentProvider)
     * If you requested one version it always returns true.
     * 
     * @return if this artifact has changed.
     */
    public boolean hasChanged();

    /**
     * @return <code>true</code> if this artifact is a bundle; <code>false</code> otherwise.
     */
    public boolean isBundle();

    /**
     * @return <code>true</code> if this artifact is a customizer that contains a resource processor; <code>false</code>
     *         otherwise.
     */
    public boolean isCustomizer();

    /**
     * @return the filename of the artifact
     */
    public String getFilename();

    /**
     * @return the (estimated) size of the artifact, in bytes, >= 0L. If -1L, the size is unknown.
     */
    public long getSize();

    /**
     * @return the symbolic name, if this artifact is a bundle.
     */
    public String getSymbolicName();

    /**
     * @return the version, if this artifact is a bundle. If it is an artifact, this function will always return
     *         "0.0.0".
     */
    public String getVersion();

    /**
     * @return the url to the artifact data.
     */
    public URL getUrl();

    /**
     * @return the processor Pid to be used for this resource, if any.
     */
    public String getProcessorPid();

    /**
     * @return a set of attributes that describes this artifact in a manifest.
     * @param fixPackage
     *            Indicating whether this set of headers is intended to be part of a fixpackage.
     */
    public Attributes getManifestAttributes(boolean fixPackage);

}
