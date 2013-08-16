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

import org.apache.ace.agent.IdentificationHandler;

/**
 * Default identification handler that reads the identity from the configuration using key
 * {@link IDENTIFICATION_CONFIG_KEY}.
 * 
 */
public class IdentificationHandlerImpl implements IdentificationHandler {

    /**
     * Configuration key for the default identification handler. The value must be a single file-system and URL safe
     * string.
     */
    // TODO move to and validate in configuration handler?
    public static final String IDENTIFICATION_CONFIG_KEY = "agent.discovery";

    private final AgentContext m_agentContext;

    public IdentificationHandlerImpl(AgentContext agentContext) {
        m_agentContext = agentContext;
    }

    // TODO add a default fallback?
    @Override
    public String getIdentification() {
        String configValue = m_agentContext.getConfigurationHandler().getMap().get(IDENTIFICATION_CONFIG_KEY);
        if (configValue == null)
            return "defaultTargetID";
        configValue = configValue.trim();
        if (configValue.equals(""))
            return null;
        return configValue;
    }
}
