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

package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;

/**
 * Defines methods for removing associations.
 */
public interface AssociationManager {

    void createArtifact2FeatureAssociation(ArtifactObject artifact, FeatureObject feature);

    void createDistribution2TargetAssociation(DistributionObject distribution, StatefulTargetObject target);

    void createFeature2DistributionAssociation(FeatureObject feature, DistributionObject distribution);

    /**
     * @param association
     */
    void removeAssociation(Artifact2FeatureAssociation association);

    /**
     * @param association
     */
    void removeAssociation(Distribution2TargetAssociation association);

    /**
     * @param association
     */
    void removeAssociation(Feature2DistributionAssociation association);
}
