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

import org.apache.ace.client.repository.AssociationRepository;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface to a Distribution2TargetAssociationRepository. The functionality is defined by the generic AssociationRepository.
 */
@ProviderType
public interface Distribution2TargetAssociationRepository extends AssociationRepository<DistributionObject, TargetObject, Distribution2TargetAssociation> {
    /**
     * Creates an assocation from a given distribution to multiple targets, which correspond to the given
     * filter string. For parameters to use in the filter, see <code>TargetObject</code>'s <code>KEY_</code> constants.
     * @param distribution A distribution object for the left side of this association.
     * @param targetFilter An LDAP-filter for the targets to use.
     * @return The newly created association.
     */
    public Distribution2TargetAssociation createDistribution2TargetFilter(DistributionObject distribution, String targetFilter);
}
