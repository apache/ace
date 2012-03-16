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
package org.apache.ace.client.repository.impl;

import java.util.Map;

import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
/**
 * Implementation class for the License2GatewayAssociationRepository. For 'what it does', see License2GatewayAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */

public class License2GatewayAssociationRepositoryImpl extends AssociationRepositoryImpl<DistributionObject, TargetObject, License2GatewayAssociationImpl, Distribution2TargetAssociation> implements Distribution2TargetAssociationRepository {
    private final static String XML_NODE = "licenses2gateways";

    private final LicenseRepositoryImpl m_licenseRepository;
    private final GatewayRepositoryImpl m_gatewayRepository;

    public License2GatewayAssociationRepositoryImpl(LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository, ChangeNotifier notifier) {
        super(notifier, XML_NODE);
        m_licenseRepository = licenseRepository;
        m_gatewayRepository = gatewayRepository;
    }

    @Override
    License2GatewayAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new License2GatewayAssociationImpl(attributes, this, m_licenseRepository, m_gatewayRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    License2GatewayAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new License2GatewayAssociationImpl(attributes, tags, this, m_licenseRepository, m_gatewayRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    License2GatewayAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new License2GatewayAssociationImpl(reader, this, m_licenseRepository, m_gatewayRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    public Distribution2TargetAssociation createDistribution2TargetFilter(DistributionObject license, String gatewayFilter) {
        try {
            m_gatewayRepository.createFilter(gatewayFilter);
        }
        catch (InvalidSyntaxException ise) {
            throw new IllegalArgumentException("Gateway filter '" + gatewayFilter + "' cannot be parsed into a valid Filter.", ise);
        }

        return create(license.getAssociationFilter(null), gatewayFilter);
    }
}
