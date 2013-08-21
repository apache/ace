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
package org.apache.ace.agent.impl;

import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.IdentificationHandler;

/**
 * Default identification handler that reads the identity from the configuration using key
 * {@link IDENTIFICATION_CONFIG_KEY}.
 * 
 */
public class IdentificationHandlerImpl extends HandlerBase implements IdentificationHandler {

    public static final String COMPONENT_IDENTIFIER = "identification";
    public static final String CONFIG_KEY_BASE = ConfigurationHandlerImpl.CONFIG_KEY_NAMESPACE + "." + COMPONENT_IDENTIFIER;

    public IdentificationHandlerImpl() {
        super(COMPONENT_IDENTIFIER);
    }

    /**
     * Configuration key for the default identification handler. The value must be a single file-system and URL safe
     * string.
     */
    public static final String CONFIG_KEY_IDENTIFICATION = CONFIG_KEY_BASE + ".agentId";
    public static final String CONFIG_DEFAULT_AGENTID = "defaultTargetID";

    @Override
    public String getAgentId() {
        ConfigurationHandler configurationHandler = getAgentContext().getConfigurationHandler();
        String configValue = configurationHandler.get(CONFIG_KEY_IDENTIFICATION, CONFIG_DEFAULT_AGENTID);
        return configValue;
    }
}
