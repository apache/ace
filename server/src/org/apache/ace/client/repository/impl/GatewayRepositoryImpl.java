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

import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.repository.GatewayRepository;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the GatewayRepository. For 'what it does', see GatewayRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 */
public class GatewayRepositoryImpl extends ObjectRepositoryImpl<GatewayObjectImpl, GatewayObject> implements GatewayRepository {
    private final static String XML_NODE = "gateways";

    public GatewayRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        return new GatewayObjectImpl(attributes, tags, this);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(Map<String, String> attributes) {
        return new GatewayObjectImpl(attributes, this);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new GatewayObjectImpl(reader, this);
    }
}
