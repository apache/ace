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
package org.apache.ace.client.repository;

import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Factory service for creating and destroying sessions. Sessions are identified by some kind
 * of identification. This identification is also used as a service property in case there is
 * one service for each session. The property name for this is <code>service.sid</code>, the
 * service session ID. It is also used to listen to session specific events, in which case this
 * same property is part of the actual event so it can be used in event filters.
 */
@ProviderType
public interface SessionFactory {
    /** Session ID for session specific service or event. */
    public static final String SERVICE_SID = "service.sid";
    
    /**
     * Create a new session based on the supplied session ID and configuration. Supplying a
     * session configuration is optional. It can contain parameters to specifically configure
     * the session.
     *
     * @param sessionID the session ID
     * @param sessionConfiguration the session configuration, if any
     */
    public void createSession(String sessionID, Map sessionConfiguration);

    /**
     * Destroy an existing session supplied on the supplied session ID.
     *
     * @param sessionID the session ID
     */
    public void destroySession(String sessionID);
}