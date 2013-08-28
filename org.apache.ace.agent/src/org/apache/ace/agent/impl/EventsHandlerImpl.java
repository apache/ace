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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.EventsHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Default thread-safe {@link EventsHandler} implementation that tracks external {@link EventListener.class} services.
 * Agent handles can manages their own listeners directly using {@link #addListener(EventListener)} and
 * {@link #removeListener(EventListener)}.
 */
public class EventsHandlerImpl extends ComponentBase implements EventsHandler {

    private final List<EventListener> m_listeners = new CopyOnWriteArrayList<EventListener>();
    private final BundleContext m_bundleContext;

    private ServiceTracker m_tracker;

    public EventsHandlerImpl(BundleContext bundleContext) throws Exception {
        super("events");
        m_bundleContext = bundleContext;
        Filter listenerFilter = m_bundleContext.createFilter("(" + Constants.OBJECTCLASS + "=" + EventListener.class.getName() + ")");
        m_tracker = new ServiceTracker(m_bundleContext, listenerFilter, new ServiceTrackerCustomizer() {

            @Override
            public Object addingService(ServiceReference reference) {
                Object service = m_bundleContext.getService(reference);
                addListener((EventListener) service);
                return service;
            }

            @Override
            public void removedService(ServiceReference reference, Object service) {
                removeListener((EventListener) service);
            }

            @Override
            public void modifiedService(ServiceReference reference, Object service) {
            }
        });
    }

    @Override
    protected void onStart() throws Exception {
        m_tracker.open();
    }

    @Override
    protected void onStop() throws Exception {
        m_tracker.close();
        m_listeners.clear();
    }

    @Override
    public void postEvent(final String topic, final Map<String, String> payload) {
        for (final EventListener listener : m_listeners) {
            getExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.handle(topic, payload);
                    }
                    catch (Exception e) {
                        logWarning("Exception while posting event", e);
                    }
                }
            });
        }
    }

    @Override
    public void sendEvent(final String topic, final Map<String, String> payload) {
        for (final EventListener listener : m_listeners) {
            try {
                listener.handle(topic, payload);
            }
            catch (Exception e) {
                logWarning("Exception while sending event", e);
            }
        }
    }

    @Override
    public void addListener(EventListener listener) {
        m_listeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        m_listeners.remove(listener);
    }
}
