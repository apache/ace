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

import org.apache.ace.client.repository.RepositoryObject;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * ChangeNotifierManager handles a number of ChangeNotifiers, so there is only one dependency on EventAdmin; this
 * manager directs all calls from the ChangeNotifiers to the 'real' EventAdmin.
 */
public class ChangeNotifierManager implements EventAdmin {
    private volatile EventAdmin m_eventAdmin; /* Will be injected by dependency manager */

    /**
     * Creates and configures a ChangeNotifier for use with the given topics.
     * 
     * @param privateTopicRoot
     *            The root of all private topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param publicTopicRoot
     *            The root of all public topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param entityRoot
     *            A class-specific root for the class which will use this ChangeNotifierImpl.
     * @return The newly configured ChangeNotifier.
     */
    public ChangeNotifier getConfiguredNotifier(String entityRoot, String sessionID) {
        return new ChangeNotifierImpl(this, RepositoryObject.PRIVATE_TOPIC_ROOT, RepositoryObject.PUBLIC_TOPIC_ROOT, entityRoot, sessionID);
    }

    /**
     * Creates and configures a ChangeNotifier for use with the given topics.
     * 
     * @param privateTopicRoot
     *            The root of all private topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param publicTopicRoot
     *            The root of all public topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param entityRoot
     *            A class-specific root for the class which will use this ChangeNotifierImpl.
     * @return The newly configured ChangeNotifier.
     */
    public ChangeNotifier getConfiguredNotifier(String privateTopicRoot, String publicTopicRoot, String entityRoot, String sessionID) {
        return new ChangeNotifierImpl(this, privateTopicRoot, publicTopicRoot, entityRoot, sessionID);
    }

    public void postEvent(Event event) {
        m_eventAdmin.postEvent(event);
    }

    public void sendEvent(Event event) {
        m_eventAdmin.sendEvent(event);
    }

}
