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
package org.apache.ace.client.repositoryuseradmin.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;

/**
 * RoleImpl works as an implements of Role, User and Group for external purposes, and
 * as a value object for use by the {@link RepositoryUserAdminImpl}.
 */
public class RoleImpl implements Role, User, Group {
    private final String m_name;
    private final int m_type;
    private final StringOnlyDictionary m_properties = new StringOnlyDictionary();
    private final StringOnlyDictionary m_credentials = new StringOnlyDictionary();
    private final Set<Role> m_members = new HashSet<Role>();

    public RoleImpl(String name, int type) {
        if (name == null) {
            throw new IllegalArgumentException("Name can not be null");
        }
        m_name = name;
        m_type = type;
    }

    public String getName() {
        return m_name;
    }

    @SuppressWarnings("unchecked")
    public Dictionary getProperties() {
        return m_properties;
    }

    public int getType() {
        return m_type;
    }

    @SuppressWarnings("unchecked")
    public Dictionary getCredentials() {
        return m_credentials;
    }

    public boolean hasCredential(String key, Object value) {
        if (value == null) {
            return false;
        }

        // Credentials can be both Strings or byte[] s.
        Object credential = m_credentials.get(key);
        if (credential instanceof String) {
            return ((String) credential).equals(value);
        }
        else if (credential instanceof byte[]) {
            return Arrays.equals((byte[]) value, (byte[]) credential);
        }

        return false;
    }

    public boolean addMember(Role role) {
        return m_members.add(role);
    }

    public boolean addRequiredMember(Role role) {
        throw new UnsupportedOperationException("addRequiredMember is not supported by RepositoryUserAdmin.");
    }

    public Role[] getMembers() {
        List<Role> result = new ArrayList<Role>();
        for (Role role : m_members) {
            result.add(role);
        }
        return result.toArray(new Role[result.size()]);
    }

    public Role[] getRequiredMembers() {
        throw new UnsupportedOperationException("getRequiredMembers is not supported by RepositoryUserAdmin.");
    }

    public boolean removeMember(Role role) {
        return m_members.remove(role);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RoleImpl)) {
            return false;
        }
        return m_name.equals(((RoleImpl) other).m_name) && (m_type == ((RoleImpl) other).m_type);
    }

    /**
     * A specialization of the dictionary that only supports String keys,
     * and String or byte[] values.
     */
    @SuppressWarnings("unchecked")
    private static final class StringOnlyDictionary extends Dictionary {
        private final Dictionary m_dict = new Hashtable<String, String>();

        @Override
        public Enumeration elements() {
            return m_dict.elements();
        }

        @Override
        public Object get(Object key) {
            return m_dict.get(key);
        }

        @Override
        public boolean isEmpty() {
            return m_dict.isEmpty();
        }

        @Override
        public Enumeration keys() {
            return m_dict.keys();
        }

        @Override
        public Object put(Object key, Object value) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("key should be of type String, not " + key.getClass().getName());
            }
            if (!(value instanceof String) && !(value instanceof byte[])) {
                throw new IllegalArgumentException("value should be of type String or byte[], not " + value.getClass().getName());
            }
            return m_dict.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("key should be of type String, not " + key.getClass().getName());
            }
            return m_dict.remove(key);
        }

        @Override
        public int size() {
            return m_dict.size();
        }
    }

    /**
     * Determines the names of the groups that this role is a member of.
     */
    String[] getMemberships(RepositoryUserAdminImpl parent) {
        // TODO For performance reasons, we could cache this list in the future.
        List<String> result = new ArrayList<String>();
        try {
            for (Role role : parent.getRoles(null)) {
                if (role instanceof Group) {
                    for (Role member : ((Group) role).getMembers()) {
                        if (equals(member)) {
                            result.add(role.getName());
                        }
                    }
                }
            }
        }
        catch (InvalidSyntaxException e) {
            // will not happen, since we pass in a null filter
        }
        return result.toArray(new String[result.size()]);
    }

}
