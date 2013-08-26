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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Simple service dependency tracker that tracks a number of required dependencies and provides life-cycle.
 */
public class DependencyTrackerImpl {

    interface LifecycleCallbacks {
        void started();

        void stopped();
    }

    interface DependencyCallback {
        void updated(Object service);
    }

    private final Set<ServiceDependency> m_dependencies = new HashSet<ServiceDependency>();
    private final BundleContext m_bundleContext;
    private final LifecycleCallbacks m_callbacks;
    private volatile boolean m_tracking = false;
    private volatile boolean m_started = false;

    public DependencyTrackerImpl(BundleContext bundleContext, LifecycleCallbacks callbacks) {
        m_bundleContext = bundleContext;
        m_callbacks = callbacks;
    }

    public BundleContext getBundleContext() {
        return m_bundleContext;
    }

    public void addDependency(Class<?> iface, String extraFilter, DependencyCallback inject) throws Exception {
        synchronized (this) {
            if (m_tracking) {
                throw new IllegalStateException("Can not add dependecies while tracking");
            }
        }
        Filter filter = null;
        if (extraFilter != null) {
            filter = FrameworkUtil.createFilter("(&(" + Constants.OBJECTCLASS + "=" + iface.getName() + ")" + extraFilter + ")");
        }
        else {
            filter = FrameworkUtil.createFilter("(" + Constants.OBJECTCLASS + "=" + iface.getName() + ")");
        }
        ServiceDependency dependency = new ServiceDependency(this, filter, inject);
        m_dependencies.add(dependency);
    }

    public void startTracking() throws Exception {
        synchronized (this) {
            if (m_tracking) {
                throw new IllegalStateException("Allready started tracking");
            }
            m_tracking = true;
        }
        for (ServiceDependency dependency : m_dependencies) {
            dependency.startTracking();
        }
    }

    public void stopTracking() {
        synchronized (this) {
            if (!m_tracking) {
                throw new IllegalStateException("Did not start tracking yet");
            }
            m_tracking = false;
        }
        for (ServiceDependency dependency : m_dependencies) {
            dependency.stopTracking();
        }
    }

    private void update() {
        // As this is a simple internal implementation we assume we can safely invoke
        // callbacks while holding locks.
        synchronized (this) {
            if (!m_tracking) {
                return;
            }
            if (dependenciesAvailable()) {
                if (m_started) {
                    stopCallback();
                }
                serviceCallbacks();
                startCallback();
            }
            else {
                stopCallback();
                serviceCallbacks();
            }
        }
    }

    private boolean dependenciesAvailable() {
        boolean available = true;
        for (ServiceDependency dependency : m_dependencies) {
            if (dependency.getService() == null) {
                available = false;
                break;
            }
        }
        return available;
    }

    private void startCallback() {
        try {
            m_callbacks.started();
            m_started = true;
        }
        catch (Exception e) {
            // really must not happen
            e.printStackTrace();
        }
    }

    private void stopCallback() {
        try {
            m_callbacks.stopped();
            m_started = false;
        }
        catch (Exception e) {
            // really must not happen
            e.printStackTrace();
        }
    }

    private void serviceCallbacks() {
        for (ServiceDependency dependency : m_dependencies) {
            try {
                dependency.invokeCallback();
            }
            catch (Exception e) {
                // really must not happen
                e.printStackTrace();
            }
        }
    }

    private static class ServiceDependency {

        private final DependencyTrackerImpl m_manager;
        private final Filter m_filter;
        private final DependencyCallback m_calback;
        private final ServiceTracker m_tracker;
        private volatile Object m_service;

        public ServiceDependency(DependencyTrackerImpl manager, Filter filter, DependencyCallback callback) throws Exception {
            m_manager = manager;
            m_filter = filter;
            m_calback = callback;
            m_tracker = new ServiceDependencyTracker(this);
        }

        public BundleContext getBundleContext() {
            return m_manager.getBundleContext();
        }

        public Filter getFilter() {
            return m_filter;
        }

        public Object getService() {
            return m_service;
        }

        public void startTracking() {
            if (m_tracker == null) {
            }
            m_tracker.open();
        }

        public void stopTracking() {
            m_tracker.close();
        }

        void invokeCallback() {
            if (m_calback != null) {
                m_calback.updated(m_service);
            }
        }

        void changed(Object service) {
            // Sync on manager to ensure all dependency updates happen in order
            synchronized (m_manager) {
                m_service = service;
                m_manager.update();
            }
        }
    }

    /**
     * Custom service tracker to simply construction.
     * 
     */
    private static class ServiceDependencyTracker extends ServiceTracker {

        public ServiceDependencyTracker(ServiceDependency dependency) {
            super(dependency.getBundleContext(), dependency.getFilter(), new ServiceDependencyTrackerCustomizer(dependency));

        }
    }

    /**
     * Tracker customizer that calls AgentContextDependency#changed with the highest matching service whenever something
     * changes.
     */
    private static class ServiceDependencyTrackerCustomizer implements ServiceTrackerCustomizer {

        private final Map<ServiceReference, Object> m_trackedServices = new HashMap<ServiceReference, Object>();
        private final ServiceDependency m_dependency;
        private volatile Object m_service;

        public ServiceDependencyTrackerCustomizer(ServiceDependency dependency) {
            m_dependency = dependency;
        }

        @Override
        public Object addingService(ServiceReference reference) {
            Object service = m_dependency.getBundleContext().getService(reference);
            synchronized (m_trackedServices) {
                m_trackedServices.put(reference, service);
                checkForUpdate();
                return service;
            }
        }

        @Override
        public void modifiedService(ServiceReference reference, Object service) {
            synchronized (m_trackedServices) {
                m_trackedServices.put(reference, service);
                checkForUpdate();
            }
        }

        @Override
        public void removedService(ServiceReference reference, Object service) {
            synchronized (m_trackedServices) {
                m_trackedServices.remove(reference);
                checkForUpdate();
            }
        }

        private void checkForUpdate() {
            ServiceReference highestReference = null;
            if (!m_trackedServices.isEmpty()) {
                for (ServiceReference reference : m_trackedServices.keySet()) {
                    if (highestReference == null || highestReference.compareTo(reference) < 1) {
                        highestReference = reference;
                    }
                }
            }
            Object service = highestReference == null ? null : m_trackedServices.get(highestReference);
            if (m_service == null || m_service != service) {
                m_service = service;
                m_dependency.changed(service);
            }
        }
    }
}
