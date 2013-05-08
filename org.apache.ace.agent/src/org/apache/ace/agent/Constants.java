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
package org.apache.ace.agent;

import org.apache.ace.agent.impl.AgentFactory;

/**
 * Compile time constants for the bundle. Only located in the API package for development time visbility.
 * 
 */
public interface Constants {

    /**
     * Configuration PID for the {@link AgentFactory}
     */
    String FACTORY_PID = "org.apache.ace.agent.factory";

    /**
     * Configuration PID for the {@link ConfigurationHandler}
     */
    String CONFIG_PID = "org.apache.ace.agent.config";

    /**
     * Configuration key for the list of component factories.
     */
    String CONFIG_FACTORIES_KEY = "system.factories";

    /**
     * Configuration key for the list of extensions activators.
     */
    String CONFIG_ACTIVATORS_KEY = "system.activators";
}
