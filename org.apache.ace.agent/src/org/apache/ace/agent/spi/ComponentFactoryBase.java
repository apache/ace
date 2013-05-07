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
package org.apache.ace.agent.spi;

import java.util.Map;
import java.util.Properties;

/**
 * Component factory base class that provides some convenience methods for concrete implementations.
 */
public abstract class ComponentFactoryBase implements ComponentFactory {

    /**
     * Returns the agent identifier from a configuration.
     * 
     * @param configuration
     *            The configuration
     * @return The identifier
     */
    protected String getAgentIdentifier(Map<String, String> configuration) {
        return configuration.get("agent");
    }

    /**
     * Returns mutable service properties with agent identifier pre-configured.
     * 
     * @param configuration
     *            The configuration
     * @return The properties
     */
    protected Properties getAgentproperties(Map<String, String> configuration) {
        Properties properties = new Properties();
        properties.put("agent", getAgentIdentifier(configuration));
        return properties;
    }

    /**
     * Returns a service filter that scopes to the agent identifier. Optionally wraps a base filter.
     * 
     * @param configuration
     *            The configuration
     * @param base
     *            The optional base filter
     * @return The filter
     */
    protected String getAgentFilter(Map<String, String> configuration, String base) {
        if (base == null) {
            return "(agent=" + getAgentIdentifier(configuration) + ")";
        }
        else {
            return "(&(agent=" + getAgentIdentifier(configuration) + ")" + base + ")";
        }
    }
}
