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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Simple service dependency tracker that tracks a number of required dependencies and provides life-cycle.
 */
public class DependencyTrackerImpl {
    /**
     * Called when an individual dependency is (no longer) satisfied.
     */
    static interface DependencyCallback {
        /**
         * Called when a dependency is updated.
         * 
         * @param service
         *            the new dependency, can be <code>null</code> in case the dependency is no longer available.
         */
        void updated(Object service);
    }

    /**
     * Callback interface for reporting the state of the tracked dependencies.
     */
    static interface LifecycleCallback {
        /**
         * Called when all dependencies are satisfied.
         */
        void componentStarted(BundleContext context) throws Exception;

        /**
         * Called when one or more dependencies are no longer satisfied.
         */
        void componentStopped(BundleContext context) throws Exception;
    }

    /**
     * Represents an actual dependency on an OSGi service.
     */
    private static class ServiceDependency<T> {
        private final DependencyTrackerImpl m_manager;
        private final DependencyCallback m_calback;
        private final ServiceTracker<T, T> m_tracker;
        // the actual tracked service...
        private final AtomicReference<T> m_serviceRef;

        public ServiceDependency(DependencyTrackerImpl manager, String filterString, DependencyCallback callback) throws Exception {
            m_manager = manager;
            m_calback = callback;

            m_tracker = new ServiceDependencyTracker<>(this, manager.getBundleContext(), FrameworkUtil.createFilter(filterString));
            m_serviceRef = new AtomicReference<>();
        }

        public Object getService() {
            return m_serviceRef.get();
        }

        public boolean isServiceAvailable() {
            return getService() != null;
        }

        public void startTracking() {
            m_tracker.open();
        }

        public void stopTracking() {
            m_tracker.close();
        }

        void changed(ServiceReference<T> ref) {
            T service = (ref == null) ? null : m_manager.getBundleContext().getService(ref);
            T oldService;
            do {
                oldService = m_serviceRef.get();
            }
            while (!m_serviceRef.compareAndSet(oldService, service));

            // Check on reference(!) to determine whether the service is changed...
            if (oldService != service) {
                if (m_calback != null) {
                    m_calback.updated(service);
                }

                m_manager.update();
            }
        }
    }

    /**
     * Tracker customizer that calls AgentContextDependency#changed with the highest matching service whenever something
     * changes.
     */
    private static class ServiceDependencyTracker<T> extends ServiceTracker<T, T> {
        private final CopyOnWriteArrayList<ServiceReference<T>> m_trackedServiceRefs;
        private final ServiceDependency<T> m_dependency;

        public ServiceDependencyTracker(ServiceDependency<T> dependency, BundleContext context, Filter filter) {
            super(context, filter, null);
            m_dependency = dependency;
            m_trackedServiceRefs = new CopyOnWriteArrayList<>();
        }

        @Override
        public T addingService(ServiceReference<T> reference) {
            if (m_trackedServiceRefs.addIfAbsent(reference)) {
                checkForUpdate();
            }
            return super.addingService(reference);
        }

        @Override
        public void modifiedService(ServiceReference<T> reference, T service) {
            checkForUpdate();
        }

        @Override
        public void removedService(ServiceReference<T> reference, T service) {
            if (m_trackedServiceRefs.remove(reference)) {
                checkForUpdate();
            }
        }

        private void checkForUpdate() {
            ServiceReference<T> highestReference = null;
            for (ServiceReference<T> reference : m_trackedServiceRefs) {
                if (highestReference == null || highestReference.compareTo(reference) < 1) {
                    highestReference = reference;
                }
            }

            m_dependency.changed(highestReference);
        }
    }

    private final BundleContext m_bundleContext;
    private final LifecycleCallback m_callback;
    private final CopyOnWriteArrayList<ServiceDependency<?>> m_dependencies;
    private final AtomicBoolean m_tracking;
    private final AtomicBoolean m_started;

    /**
     * Creates a new {@link DependencyTrackerImpl} instance.
     * 
     * @param bundleContext
     *            the bundle context;
     * @param callback
     *            the component callback.
     */
    public DependencyTrackerImpl(BundleContext bundleContext, LifecycleCallback callback) {
        m_bundleContext = bundleContext;
        m_callback = callback;

        m_dependencies = new CopyOnWriteArrayList<>();
        m_tracking = new AtomicBoolean(false);
        m_started = new AtomicBoolean(false);
    }

    /**
     * Adds a dependency to track.
     * 
     * @param iface
     *            the interface of the dependency to track;
     * @param extraFilter
     *            an optional filter for the tracked dependency;
     * @param callback
     *            the callback to call when the dependency comes (un)available.
     */
    public void addDependency(Class<?> iface, String extraFilter, DependencyCallback callback) throws Exception {
        if (m_tracking.get()) {
            throw new IllegalStateException("Can not add new dependency while tracking is started!");
        }

        String filter = String.format("(%s=%s)", Constants.OBJECTCLASS, iface.getName());
        if (extraFilter != null) {
            filter = String.format("(&%s%s)", filter, extraFilter);
        }

        m_dependencies.addIfAbsent(new ServiceDependency<>(this, filter, callback));
    }

    public BundleContext getBundleContext() {
        return m_bundleContext;
    }

    /**
     * Starts tracking all dependencies, if all dependencies are satisfied,
     * {@link LifecycleCallback#componentStarted(BundleContext)} will be called. For each satisfied dependency,
     * {@link DependencyCallback#updated(Object)} is called.
     * 
     * @throws IllegalStateException
     *             in case this tracker is already started.
     */
    public void startTracking() throws Exception {
        // This method should be called once and only once...
        if (!m_tracking.compareAndSet(false, true)) {
            throw new IllegalStateException("Already started tracking!");
        }

        for (ServiceDependency<?> dependency : m_dependencies) {
            dependency.startTracking();
        }
    }

    /**
     * Stops tracking of dependencies. For each tracked dependency, {@link DependencyCallback#updated(Object)} is called
     * with a <code>null</code> value.
     * 
     * @throws IllegalStateException
     *             in case this tracker is already started.
     */
    public void stopTracking() {
        // This method should be called once and only once...
        if (!m_tracking.compareAndSet(true, false)) {
            throw new IllegalStateException("Did not start tracking yet");
        }

        for (ServiceDependency<?> dependency : m_dependencies) {
            dependency.stopTracking();
        }
    }

    /**
     * Called for each change in the dependency set. It will call
     * {@link LifecycleCallback#componentStopped(BundleContext)} if needed, and
     * {@link LifecycleCallback#componentStarted(BundleContext)} when all dependencies are met.
     */
    public void update() {
        stopComponent();

        if (allDependenciesAvailable()) {
            startComponent();
        }
    }

    /**
     * @return <code>true</code> if all dependencies are available, <code>false</code> otherwise.
     */
    final boolean allDependenciesAvailable() {
        for (ServiceDependency<?> dependency : m_dependencies) {
            if (!dependency.isServiceAvailable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to start the component, if it is not already started.
     */
    final void startComponent() {
        // Only call our callback when we're actually starting tracking dependencies...
        if (m_started.compareAndSet(false, true)) {
            try {
                m_callback.componentStarted(m_bundleContext);
            }
            catch (Exception e) {
                // really must not happen
                e.printStackTrace();
            }
        }
    }

    /**
     * Tries to stop the component, if it is not already stopped.
     */
    final void stopComponent() {
        // Only call our callback when we're actually started tracking dependencies...
        if (m_started.compareAndSet(true, false)) {
            try {
                m_callback.componentStopped(m_bundleContext);
            }
            catch (Exception e) {
                // really must not happen
                e.printStackTrace();
            }
        }
    }
}
