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

import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the DistributionRepository. For 'what it does', see DistributionRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 */
public class DistributionRepositoryImpl extends ObjectRepositoryImpl<DistributionObjectImpl, DistributionObject> implements DistributionRepository {
    private final static String XML_NODE = "distributions";

    public DistributionRepositoryImpl(ChangeNotifier notifier, RepositoryConfiguration repoConfig) {
        super(notifier, XML_NODE, repoConfig);
    }

    @Override
    DistributionObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        return new DistributionObjectImpl(attributes, tags, this);
    }

    @Override
    DistributionObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new DistributionObjectImpl(reader, this);
    }
}
