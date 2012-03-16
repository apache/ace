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
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the License2GatewayAssociation. For 'what it does', see License2GatewayAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class License2GatewayAssociationImpl extends AssociationImpl<DistributionObject, TargetObject, Distribution2TargetAssociation> implements Distribution2TargetAssociation {
    private final static String XML_NODE = "license2gateway";

    public License2GatewayAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(attributes, notifier, DistributionObject.class, TargetObject.class, licenseRepository, gatewayRepository, XML_NODE);
    }
    public License2GatewayAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, DistributionObject.class, TargetObject.class, licenseRepository, gatewayRepository, XML_NODE);
    }
    public License2GatewayAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(reader, notifier, DistributionObject.class, TargetObject.class, null, null, licenseRepository, gatewayRepository, XML_NODE);
    }
}
