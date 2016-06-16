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

import java.util.Map;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Listener interface for consumers that wish to be notified of agent events. This includes events deployment events as
 * defined by the OSGi DeploymentAdmin specification.
 */
@ConsumerType
public interface EventListener {

    /**
     * Event callback.
     * 
     * @param topic The topic string
     * @param payload An unmodifiable map
     */
    void handle(String topic, Map<String, String> payload);
}
