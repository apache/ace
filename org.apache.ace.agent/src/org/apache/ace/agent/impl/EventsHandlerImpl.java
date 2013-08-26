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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * InternalEvents that posts events to internal handlers and external admins.
 */
public class EventsHandlerImpl implements EventsHandler {

    private final Map<EventHandler, String[]> m_eventHandlers = new HashMap<EventHandler, String[]>();

    public void postEvent(String topic, Dictionary<String, String> payload) {
        Event event = new Event(topic, payload);
        postEvent(event);
    }

    public void postEvent(Event event) {
        sendInternal(event);
        sendExternal(event);
    }

    void registerHandler(EventHandler eventHandler, String[] topics) {
        synchronized (m_eventHandlers) {
            m_eventHandlers.put(eventHandler, topics);
        }
    }

    void unregisterHandler(EventHandler eventHandler) {
        synchronized (m_eventHandlers) {
            m_eventHandlers.remove(eventHandler);
        }
    }

    private void sendInternal(Event event) {
        String topic = event.getTopic();
        synchronized (m_eventHandlers) {
            for (Entry<EventHandler, String[]> entry : m_eventHandlers.entrySet()) {
                for (String interest : entry.getValue()) {
                    if ((interest.endsWith("*") && topic.startsWith(interest.substring(0, interest.length() - 1))
                    || topic.equals(interest))) {
                        entry.getKey().handleEvent(event);
                        break;
                    }
                }
            }
        }
    }

    private void sendExternal(Event event) {
        // TODO this requires looking for all service references and invoking any found admins using reflection
    }

}
