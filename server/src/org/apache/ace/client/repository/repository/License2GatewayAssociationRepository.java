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
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;

/**
 * Interface to a License2GatewayAssociationRepository. The functionality is defined by the generic AssociationRepository.
 */
public interface License2GatewayAssociationRepository extends AssociationRepository<LicenseObject, GatewayObject, License2GatewayAssociation> {
    /**
     * Creates an assocation from a given license to multiple gateways, which correspond to the given
     * filter string. For parameters to use in the filter, see <code>GatewayObject</code>'s <code>KEY_</code> constants.
     * @param license A license object for the left side of this association.
     * @param gatewayFilter An LDAP-filter for the gateways to use.
     * @return The newly created association.
     */
    public License2GatewayAssociation createLicense2GatewayFilter(LicenseObject license, String gatewayFilter);
}
