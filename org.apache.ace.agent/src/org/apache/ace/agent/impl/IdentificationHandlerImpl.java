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

import static org.apache.ace.agent.AgentConstants.CONFIG_IDENTIFICATION_AGENTID;

import org.apache.ace.agent.IdentificationHandler;

/**
 * Default identification handler that reads the identity from the configuration using key
 * {@link IDENTIFICATION_CONFIG_KEY}.
 * 
 */
public class IdentificationHandlerImpl extends ComponentBase implements IdentificationHandler {
    /** Default name to use for a new target. */
    public static final String CONFIG_DEFAULT_AGENTID = "defaultTargetID";

    public IdentificationHandlerImpl() {
        super("identification");
    }

    @Override
    public String getAgentId() {
        return getConfigurationHandler().get(CONFIG_IDENTIFICATION_AGENTID, CONFIG_DEFAULT_AGENTID);
    }
}
