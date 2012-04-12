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
package org.apache.ace.authentication.impl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a basic implementation for {@link AuthenticationService} that returns the first matching user.
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * Provides a small container for {@link AuthenticationProcessor} instances.
     */
    private static class AuthenticationProcessorHolder implements Comparable<AuthenticationProcessorHolder> {
        private final ServiceReference m_serviceRef;
        private final WeakReference<AuthenticationProcessor> m_processor;

        public AuthenticationProcessorHolder(ServiceReference serviceRef, AuthenticationProcessor processor) {
            m_serviceRef = serviceRef;
            m_processor = new WeakReference<AuthenticationProcessor>(processor);
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(AuthenticationProcessorHolder other) {
            ServiceReference thatServiceRef = other.m_serviceRef;
            ServiceReference thisServiceRef = m_serviceRef;
            // Sort in reverse order so that the highest rankings come first...
            return thatServiceRef.compareTo(thisServiceRef);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof AuthenticationProcessorHolder)) {
                return false;
            }
            AuthenticationProcessorHolder other = (AuthenticationProcessorHolder) obj;
            return m_serviceRef.equals(other.m_serviceRef);
        }

        /**
         * @return the {@link AuthenticationProcessor}, can be <code>null</code> if it has been GC'd before this method call.
         */
        public AuthenticationProcessor getAuthenticationProcessor() {
            return m_processor.get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return m_serviceRef.hashCode() ^ m_processor.hashCode();
        }
    }

    private volatile UserAdmin m_userAdmin;
    private volatile LogService m_log;

    private final List<AuthenticationProcessorHolder> m_processors;

    /**
     * Creates a new {@link AuthenticationServiceImpl} instance.
     */
    public AuthenticationServiceImpl() {
        m_processors = new ArrayList<AuthenticationServiceImpl.AuthenticationProcessorHolder>();
    }

    /**
     * Creates a new {@link AuthenticationServiceImpl} instance.
     */
    AuthenticationServiceImpl(LogService log) {
        m_log = log;
        m_processors = new ArrayList<AuthenticationServiceImpl.AuthenticationProcessorHolder>();
    }

    /**
     * Authenticates a user based on the given context information.
     * <p>
     * This implementation returns the first {@link User}-object that is returned by a {@link AuthenticationProcessor} instance that can handle the given context.
     * </p>
     */
    public User authenticate(Object... context) {
        if (context == null || context.length == 0) {
            throw new IllegalArgumentException("Invalid context!");
        }

        User result = null;

        m_log.log(LogService.LOG_DEBUG, "Authenticating user for: " + context);

        final List<AuthenticationProcessor> processors = getProcessors(context);
        for (AuthenticationProcessor processor : processors) {
            result = processor.authenticate(m_userAdmin, context);
            if (result != null) {
                m_log.log(LogService.LOG_DEBUG, "Authenticated user (" + context + ") as: " + result.getName());
                break;
            }
        }

        return result;
    }

    /**
     * Returns all applicable {@link AuthenticationProcessor}s for the given context.
     * 
     * @param context the context for which to return all applicable authentication processors, cannot be <code>null</code> or an empty array.
     * @return an array of applicable authentication processors, never <code>null</code>.
     */
    final List<AuthenticationProcessor> getProcessors(Object... context) {
        final List<AuthenticationProcessorHolder> processors;
        synchronized (m_processors) {
            processors = new ArrayList<AuthenticationProcessorHolder>(m_processors);
        }
        // Sort on service ranking...
        Collections.sort(processors);

        int size = processors.size();
        
        List<AuthenticationProcessor> result = new ArrayList<AuthenticationProcessor>(size);
        for (int i = 0; i < size; i++) {
            AuthenticationProcessor authenticationProcessor = processors.get(i).getAuthenticationProcessor();
            // Can be null if it is already GC'd for some reason...
            if ((authenticationProcessor != null) && authenticationProcessor.canHandle(context)) {
                result.add(authenticationProcessor);
            }
        }

        return result;
    }

    /**
     * Called by {@link DependencyManager} upon adding a new {@link AuthenticationProcessor}.
     * 
     * @param serviceRef the service reference of the authentication processor to add;
     * @param processor the authentication processor to add.
     */
    protected void addAuthenticationProcessor(ServiceReference serviceRef, AuthenticationProcessor processor) {
        synchronized (m_processors) {
            m_processors.add(new AuthenticationProcessorHolder(serviceRef, processor));
        }
    }

    /**
     * Called by {@link DependencyManager} upon removal of a {@link AuthenticationProcessor}.
     * 
     * @param serviceRef the service reference of the authentication processor to remove;
     * @param processor the authentication processor to remove.
     */
    protected void removeAuthenticationProcessor(ServiceReference serviceRef, AuthenticationProcessor processor) {
        synchronized (m_processors) {
            m_processors.remove(new AuthenticationProcessorHolder(serviceRef, processor));
        }
    }
}
