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
package org.apache.ace.webui.domain;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.webui.NamedObject;

/**
 * Provides a small factory for creating {@link NamedObject}s for any given {@link RepositoryObject}.
 */
public final class NamedObjectFactory {

    public static NamedObject getNamedObject(RepositoryObject object) {
        if (object instanceof ArtifactObject) {
            return new NamedArtifactObject((ArtifactObject) object);
        }
        else if (object instanceof FeatureObject) {
            return new NamedFeatureObject((FeatureObject) object);
        }
        else if (object instanceof DistributionObject) {
            return new NamedDistributionObject((DistributionObject) object);
        }
        else if (object instanceof TargetObject) {
            return new NamedTargetObject((TargetObject) object);
        }
        else if (object instanceof StatefulTargetObject) {
            return new NamedStatefulTargetObject((StatefulTargetObject) object);
        }
        else if (object instanceof Association) {
            return new NamedAssociationObject((Association<?, ?>) object);
        }
        return null;
    }
}
