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

/**
 * Represents internal constants.
 */
public interface InternalConstants {
    /**
     * The feedback channel for reporting the audit events of a target.
     */
    String AUDITLOG_FEEDBACK_CHANNEL = "auditlog";
    /**
     * Internal event topic used by the default controller to fire events when the installation of a deployment package
     * <em>or</em> agent update is started. This is sent always prior to the beginning of an installation.
     */
    String AGENT_INSTALLATION_START = "agent/defaultController/installation/START";
    /**
     * Internal event topic used by the default controller to fire events when the installation of a deployment package
     * <em>or</em> agent update is complete (either or not successful).
     */
    String AGENT_INSTALLATION_COMPLETE = "agent/defaultController/installation/COMPLETE";
}
