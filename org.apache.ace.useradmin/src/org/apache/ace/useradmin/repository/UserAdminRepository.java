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
package org.apache.ace.useradmin.repository;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.ace.repository.Repository;
import org.apache.ace.useradmin.repository.xstream.GroupDTO;
import org.apache.ace.useradmin.repository.xstream.RoleDTO;
import org.apache.ace.useradmin.repository.xstream.UserDTO;
import org.apache.ace.useradmin.repository.xstream.XStreamFactory;
import org.apache.felix.useradmin.RoleFactory;
import org.apache.felix.useradmin.RoleRepositoryStore;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdminEvent;
import org.osgi.service.useradmin.UserAdminListener;

import com.thoughtworks.xstream.XStream;

/**
 * Felix UserAdmin RoleRepositoryStore implementation that's backed by an ACE Repository
 */
public class UserAdminRepository implements RoleRepositoryStore, UserAdminListener, RepoCurrentChecker {
    private final ConcurrentMap<String, Role> m_roleMap;
    private final AtomicLong m_version;
    private final ReadWriteLock m_rw;

    private volatile Repository m_repository;
    private volatile LogService m_log;

    public UserAdminRepository() {
        m_roleMap = new ConcurrentHashMap<>();
        m_version = new AtomicLong(-1L);
        m_rw = new ReentrantReadWriteLock(true /* fair */);
    }

    UserAdminRepository(Repository repo, LogService log) {
        this();
        m_repository = repo;
        m_log = log;
    }

    @Override
    public Role addRole(String name, int type) throws Exception {
        ensureRoleMapIsCurrent();

        m_rw.readLock().lock();
        try {
            if (m_roleMap.containsKey(name)) {
                return null;
            }
        }
        finally {
            m_rw.readLock().unlock();
        }

        Role role;
        switch (type) {
            case Role.USER:
                role = wrapRole(RoleFactory.createUser(name));
                break;
            case Role.GROUP:
                role = wrapRole(RoleFactory.createGroup(name));
                break;
            default:
                throw new IllegalArgumentException("Invalid group type " + type);
        }

        m_rw.writeLock().lock();
        try {
            m_roleMap.put(name, role);
            // Make sure the repository is correctly synchronized...
            m_log.log(LogService.LOG_DEBUG, "Writing role map due to adding of " + ((type == Role.USER) ? "user" : "group") + " role: " + name);

            writeRoleMap();
        }
        finally {
            m_rw.writeLock().unlock();
        }

        return role;
    }

    public void checkRepoUpToDate(Role context, AtomicLong version) throws IllegalStateException {
        long currentVersion = getMostRecentVersion();
        // NOTE: do not use local variable for `version.get()` as it appears that javac otherwise replaces it with a
        // constant...
        if ((version.get() > 0) && currentVersion != version.get()) {
            m_rw.writeLock().lock();
            try {
                m_roleMap.clear();
            }
            finally {
                m_rw.writeLock().unlock();
            }

            throw new IllegalStateException(context + " out of sync. Please refresh first!");
        }
    }

    @Override
    public Role getRoleByName(String name) throws Exception {
        if (name == null) {
            return null;
        }

        ensureRoleMapIsCurrent();

        m_rw.readLock().lock();
        try {
            return m_roleMap.get(name);
        }
        finally {
            m_rw.readLock().unlock();
        }
    }

    @Override
    public Role[] getRoles(String filterString) throws Exception {
        Filter filter = null;
        if (filterString != null) {
            filter = FrameworkUtil.createFilter(filterString);
        }

        ensureRoleMapIsCurrent();

        List<Role> allRoles;
        m_rw.readLock().lock();
        try {
            allRoles = new ArrayList<>(m_roleMap.values());
        }
        finally {
            m_rw.readLock().unlock();
        }

        List<Role> matchingRoles = new ArrayList<>();
        for (Role role : allRoles) {
            if (filter == null || filter.match(role.getProperties())) {
                matchingRoles.add(role);
            }
        }

        return matchingRoles.toArray(new Role[matchingRoles.size()]);
    }

    @Override
    public Role removeRole(String name) throws Exception {
        m_rw.writeLock().lock();
        try {
            Role role = m_roleMap.remove(name);
            if (role != null) {
                // Make sure the repository is correctly synchronized...
                m_log.log(LogService.LOG_DEBUG, "Writing role map due to removal of " + ((role.getType() == Role.USER) ? "user" : "group") + " role: " + role.getName());

                writeRoleMap();
            }
            return role;
        }
        finally {
            m_rw.writeLock().unlock();
        }
    }

    @Override
    public void roleChanged(UserAdminEvent event) {
        m_rw.writeLock().lock();
        try {
            // Make sure the repository is correctly synchronized...
            Role role = event.getRole();
            m_log.log(LogService.LOG_DEBUG, "Writing role map due to change of " + ((role.getType() == Role.USER) ? "user" : "group") + " role: " + role.getName());

            writeRoleMap();
        }
        finally {
            m_rw.writeLock().unlock();
        }
    }

    final void readRoleMap(long version) throws Exception {
        XStream instance = XStreamFactory.getInstance();

        Map<String, Role> newRoles = new HashMap<>();

        try (Reader r = new InputStreamReader(m_repository.checkout(version));
            ObjectInputStream objectInputStream = instance.createObjectInputStream(r)) {

            RoleDTO roleDto;
            List<RoleDTO> rolesWithMemberships = new ArrayList<>();

            try {
                while ((roleDto = (RoleDTO) objectInputStream.readObject()) != null) {
                    User role;
                    if (roleDto.type == Role.USER) {
                        role = RoleFactory.createUser(roleDto.name);
                    }
                    else if (roleDto.type == Role.GROUP) {
                        role = RoleFactory.createGroup(roleDto.name);
                    }
                    else {
                        throw new IllegalStateException("");
                    }
                    if (roleDto.properties != null) {
                        for (Entry<Object, Object> entry : roleDto.properties.entrySet()) {
                            role.getProperties().put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (roleDto.credentials != null) {
                        for (Entry<Object, Object> entry : roleDto.credentials.entrySet()) {
                            role.getCredentials().put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (roleDto.memberOf != null && !roleDto.memberOf.isEmpty()) {
                        rolesWithMemberships.add(roleDto);
                    }

                    newRoles.put(role.getName(), role);
                }
            }
            catch (EOFException e) {
                // Ignore, this is the way XStream let's us know we're done reading
            }

            for (RoleDTO role : rolesWithMemberships) {
                Role memberRole = newRoles.get(role.name);
                for (String memberOf : role.memberOf) {
                    Role groupRole = newRoles.get(memberOf);
                    if (groupRole == null) {
                        throw new IllegalStateException("Target group not found");
                    }

                    if (groupRole.getType() != Role.GROUP) {
                        throw new IllegalStateException("Target is not a group");
                    }

                    ((Group) groupRole).addMember(memberRole);
                }
            }
        }

        m_rw.writeLock().lock();
        try {
            // "Commit" everything...
            m_roleMap.clear();
            for (Map.Entry<String, Role> entry : newRoles.entrySet()) {
                m_roleMap.put(entry.getKey(), wrapRole(entry.getValue()));
            }

            m_version.set(version);
        }
        finally {
            m_rw.writeLock().unlock();
        }
    }

    final void writeRoleMap() {
        StringWriter writer = new StringWriter();

        List<Role> roles = new ArrayList<>(m_roleMap.values());

        XStream instance = XStreamFactory.getInstance();
        try (ObjectOutputStream stream = instance.createObjectOutputStream(writer, "roles")) {
            for (Role role : roles) {
                List<String> memberOf = memberOf(roles, role);
                if (role.getType() == Role.USER) {
                    stream.writeObject(new UserDTO((User) role, memberOf));
                }
                else if (role.getType() == Role.GROUP) {
                    GroupDTO obj = new GroupDTO((Group) role, memberOf);
                    stream.writeObject(obj);
                }
                else {
                    throw new IllegalStateException("Unsupported role type");
                }
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Failed to write role changes to the main role repository", e);
            return;
        }

        try (InputStream inputStream = new ByteArrayInputStream(writer.toString().getBytes())) {
            long fromVersion = m_version.longValue();

            if (m_repository.commit(inputStream, fromVersion)) {
                m_version.set(getMostRecentVersion());
            }
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Failed to commit role changes to the main role repository", e);
        }
    }

    /**
     * Add a wrapper around a Role that prevents changes to Users / Groups when the repository is out of sync
     * 
     * @param role
     *            User or Group role to be wrapped
     * @return a wrapped Role
     */
    protected Role wrapRole(Role role) {
        if (role.getType() == Role.USER) {
            return new RepositoryUser((User) role, m_version, this);
        }
        else if (role.getType() == Role.GROUP) {
            return new RepositoryGroup((Group) role, m_version, this);
        }
        else {
            throw new IllegalStateException("Invalid role type: " + role.getType());
        }
    }

    private boolean contains(Role needle, Role[] haystack) {
        for (Role role : haystack) {
            if (role.getType() == needle.getType() && role.getName().equals(needle.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureRoleMapIsCurrent() throws Exception {
        long actualVersion;
        long localVersion;

        boolean isCurrent;
        m_rw.readLock().lock();
        try {
            actualVersion = getMostRecentVersion();
            localVersion = m_version.longValue();

            isCurrent = !m_roleMap.isEmpty() && localVersion == actualVersion;
        }
        finally {
            m_rw.readLock().unlock();
        }

        if (!isCurrent) {
            m_log.log(LogService.LOG_DEBUG, "Reading role map as we're no longer current (" + localVersion + " <=> " + actualVersion + " )...");

            readRoleMap(actualVersion);
        }

        return isCurrent;
    }

    private long getMostRecentVersion() {
        m_rw.readLock().lock();
        try {
            return m_repository.getRange().getHigh();
        }
        catch (IOException exception) {
            m_log.log(LogService.LOG_WARNING, "Unable to query repository for most recent version!", exception);
            return -1L;
        }
        finally {
            m_rw.readLock().unlock();
        }
    }

    private List<String> memberOf(List<Role> roles, Role role) {
        List<String> memberOf = new ArrayList<>();
        for (Role r : roles) {
            if (r instanceof Group) {
                Group group = (Group) r;
                Role[] members = group.getMembers();
                if (members != null) {
                    if (contains(role, members)) {
                        memberOf.add(group.getName());
                    }
                }
            }
        }
        return memberOf;
    }
}
