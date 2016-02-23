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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a basic implementation for {@link AuthenticationService} that returns the first matching user.
 */
public class AuthenticationServiceImpl implements AuthenticationService {
    private volatile UserAdmin m_userAdmin;
    private volatile LogService m_log;

    private final CopyOnWriteArrayList<AuthenticationProcessor> m_processors;

    /**
     * Creates a new {@link AuthenticationServiceImpl} instance.
     */
    public AuthenticationServiceImpl() {
        m_processors = new CopyOnWriteArrayList<>();
    }

    /**
     * Creates a new {@link AuthenticationServiceImpl} instance.
     */
    AuthenticationServiceImpl(LogService log) {
        m_log = log;
        m_processors = new CopyOnWriteArrayList<>();
    }

    /**
     * Authenticates a user based on the given context information.
     * <p>
     * This implementation returns the first {@link User}-object that is returned by a {@link AuthenticationProcessor}
     * instance that can handle the given context.
     * </p>
     */
    public User authenticate(Object... context) {
        if (context == null || context.length == 0) {
            throw new IllegalArgumentException("Invalid context!");
        }

        User result = null;
        for (AuthenticationProcessor processor : getProcessors(context)) {
            result = processor.authenticate(m_userAdmin, context);
            if (result != null) {
                break;
            }
        }

        if (result != null) {
            m_log.log(LogService.LOG_DEBUG, String.format("Context (%s) authenticated as user %s", describeContext(context), result.getName()));
        }
        else {
            m_log.log(LogService.LOG_WARNING, String.format("Context (%s) NOT authenticated: no matching user found!", describeContext(context)));
        }

        return result;
    }

    /**
     * Returns all applicable {@link AuthenticationProcessor}s for the given context.
     * 
     * @param context
     *            the context for which to return all applicable authentication processors, cannot be <code>null</code>
     *            or an empty array.
     * @return an array of applicable authentication processors, never <code>null</code>.
     */
    final List<AuthenticationProcessor> getProcessors(Object... context) {
        List<AuthenticationProcessor> processors = new ArrayList<>(m_processors);

        Iterator<AuthenticationProcessor> iter = processors.iterator();
        while (iter.hasNext()) {
            AuthenticationProcessor authenticationProcessor = iter.next();
            if (!authenticationProcessor.canHandle(context)) {
                iter.remove();
            }
        }
        return processors;
    }

    /**
     * Called by {@link DependencyManager} upon adding a new {@link AuthenticationProcessor}.
     * 
     * @param processor
     *            the authentication processor to add.
     */
    protected void addAuthenticationProcessor(AuthenticationProcessor processor) {
        m_processors.addIfAbsent(processor);
    }

    /**
     * Called by {@link DependencyManager} upon removal of a {@link AuthenticationProcessor}.
     * 
     * @param processor
     *            the authentication processor to remove.
     */
    protected void removeAuthenticationProcessor(AuthenticationProcessor processor) {
        m_processors.remove(processor);
    }

    private String describeContext(Object... context) {
        StringBuilder sb = new StringBuilder("[");
        for (Object obj : context) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            if (obj instanceof Role) {
                sb.append(((Role) obj).getType() == Role.USER ? "User(" : "Group(");
                sb.append(((Role) obj).getName());
                sb.append(")");
            }
            else if (obj instanceof HttpServletRequest) {
                sb.append("HttpServletRequest(");
                sb.append(((HttpServletRequest) obj).getPathInfo());
                sb.append(")");
            }
            else if (obj instanceof byte[]) {
                sb.append("byte[]{").append(((byte[]) obj).length).append(")");
            }
            else {
                sb.append(obj);
            }
        }
        return sb.append("]").toString();
    }
}
