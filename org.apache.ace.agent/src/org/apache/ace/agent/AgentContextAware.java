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

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Providers interface for (extension) components.
 */
@ConsumerType
public interface AgentContextAware {

    /**
     * Called when the agent context is initializing, and is called <em>before</em> {@link #start(AgentContext)}.
     * <p>
     * Use this method to register event listeners and/or perform other forms of initialization related tasks that need
     * to be done prior to {@link #start(AgentContext)} being called.
     * </p>
     *
     * @param agentContext
     *            the agent context that is initializing, never <code>null</code>.
     * @throws Exception
     *             if the component fails to initialize, which is logged and ignored by the agent.
     */
    void init(AgentContext agentContext) throws Exception;

    /**
     * Called when the agent context is started.
     *
     * @param agentContext
     *            the agent context that is started, never <code>null</code>.
     * @throws Exception
     *             if the component fails to start, which is logged and ignored by the agent.
     */
    void start(AgentContext agentContext) throws Exception;

    /**
     * Called when the agent context is stopped.
     *
     * @throws Exception
     *             if the component fails to stop, which is logged and ignored by the agent.
     */
    void stop() throws Exception;
}
