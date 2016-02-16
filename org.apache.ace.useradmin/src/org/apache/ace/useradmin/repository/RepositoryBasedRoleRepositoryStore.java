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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.ext.impl.CachedRepositoryImpl;
import org.apache.ace.repository.ext.impl.FilebasedBackupRepository;
import org.apache.ace.repository.ext.impl.RemoteRepository;
import org.apache.ace.useradmin.repository.xstream.GroupDTO;
import org.apache.ace.useradmin.repository.xstream.RoleDTO;
import org.apache.ace.useradmin.repository.xstream.UserDTO;
import org.apache.ace.useradmin.repository.xstream.XStreamFactory;
import org.apache.felix.useradmin.RoleFactory;
import org.apache.felix.useradmin.RoleRepositoryStore;
import org.osgi.framework.BundleContext;
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
 *
 */
public class RepositoryBasedRoleRepositoryStore implements RoleRepositoryStore, UserAdminListener {
    
    private volatile BundleContext m_BundleContext;
    private volatile LogService m_log;
    private volatile RemoteRepository m_repository;    
    private volatile CachedRepository m_cachedRepository;

    private volatile AtomicLong m_version;
    private final Map<String, Role> m_roleMap = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unused" /* dependency manager callback */)
    private void start() throws IOException {
        File currentFile = m_BundleContext.getDataFile("current.xml");
        File backupFile = m_BundleContext.getDataFile("backup.xml");
        
        if (currentFile.exists()) {
            currentFile.delete();
        }
        
        if (backupFile.exists()) {
            backupFile.delete();
        }
        
        FilebasedBackupRepository backupRepo = new FilebasedBackupRepository(currentFile, backupFile);
        m_cachedRepository = new CachedRepositoryImpl(m_repository, backupRepo, CachedRepositoryImpl.UNCOMMITTED_VERSION);
    }

    @SuppressWarnings("unchecked")
    private void refreshRoleMap() throws Exception {
        m_roleMap.clear();
        XStream instance = XStreamFactory.getInstance();
        
        try (InputStream inputStream = m_cachedRepository.checkout(true);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                ObjectInputStream objectInputStream = instance.createObjectInputStream(inputStreamReader)){
            
            RoleDTO roleDto;
            List<RoleDTO> rolesWithMemberships = new ArrayList<>();
            m_version = new AtomicLong(m_cachedRepository.getMostRecentVersion());
            try {
                while ((roleDto = (RoleDTO) objectInputStream.readObject()) != null) {
                    User role;
                    if (roleDto.type == Role.USER) {
                        role = RoleFactory.createUser(roleDto.name);
                    } else if (roleDto.type == Role.GROUP) {
                        role = RoleFactory.createGroup(roleDto.name);
                    } else {
                        throw new IllegalStateException("");
                    }
                    if (roleDto.properties != null){
                        for (Entry<Object, Object> entry : roleDto.properties.entrySet()) {
                            role.getProperties().put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (roleDto.credentials != null){
                        for (Entry<Object, Object> entry : roleDto.credentials.entrySet()) {
                            role.getCredentials().put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (roleDto.memberOf != null && !roleDto.memberOf.isEmpty()){
                        rolesWithMemberships.add(roleDto);
                    }
                    
                    m_roleMap.put(role.getName(), role);
                }
            }catch (EOFException e) {
                // Ignore, this is the way XStream let's us know we're done reading
            }
            
            for (RoleDTO role : rolesWithMemberships) {
                Role memberRole = m_roleMap.get(role.name);
                for (String memberOf : role.memberOf) {
                    Role groupRole = m_roleMap.get(memberOf);
                    if (groupRole == null){
                        throw new IllegalStateException("Target group not found");
                    }
                    
                    if (groupRole.getType() != Role.GROUP) {
                        throw new IllegalStateException("Target is not a group");
                    }
                    
                    Group group = (Group) groupRole;
                    group.addMember(memberRole);
                }
            }
            
            // Wrap users and groups in repository user / group types 
            for (Entry<String, Role> roleMapEntry : m_roleMap.entrySet()) {
                m_roleMap.put(roleMapEntry.getKey(), wrapRole(roleMapEntry.getValue()));
            }
        }
    }
    
    /**
     * Add a wrapper around a Role that prevents changes to Users / Groups when the repository is out of sync
     * 
     * @param role User or Group role to be wrapped
     * @return a wrapped Role
     */
    private Role wrapRole(Role role) {
        if (role.getType() == Role.USER) {
            return new RepositoryUser((User)role, m_cachedRepository, m_version);
        } else if (role.getType() == Role.GROUP) {
            return new RepositoryUser((Group)role, m_cachedRepository, m_version);
        }else {
            throw new IllegalStateException("");
        }
    }

    @Override
    public Role getRoleByName(String name) throws Exception {
        if (name == null) {
            return null;
        }
        
        synchronized (m_roleMap) {
            if (!m_cachedRepository.isCurrent()) {
                refreshRoleMap(); 
            }
            return m_roleMap.get(name);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Role[] getRoles(String filterString) throws Exception {
        synchronized (m_roleMap) {
            if (!m_cachedRepository.isCurrent()) {
                refreshRoleMap();
            }
        
            if (filterString == null) {
                return m_roleMap.values().toArray(new Role[0]);
            }
            
            Filter filter = FrameworkUtil.createFilter(filterString);
            
            List<Role> matchingRoles = new ArrayList<>();
            for (Role role: m_roleMap.values()){
                if (filter.match(role.getProperties())){
                    matchingRoles.add(role);
                }
            }
            
            return matchingRoles.toArray(new Role[matchingRoles.size()]);
        }
    }

    @Override
    public Role addRole(String name, int type) throws Exception {
        Role role;
        switch (type) {
            case Role.USER:
                role = RoleFactory.createUser(name);                
                break;
            case Role.GROUP:
                role = RoleFactory.createGroup(name);
                break;
            default:
                throw new IllegalArgumentException("Invalid group type " + type);
        }
        synchronized (m_roleMap) {
            if (m_cachedRepository.getMostRecentVersion() == -1) {
                refreshRoleMap();
            }
            
            if (m_roleMap.containsKey(name)){
                return null;
            }
            role = wrapRole(role);
            m_roleMap.put(name, role);
            roleChanged(null);
        }
        return role;
    }
    
    @Override
    public Role removeRole(String name) throws Exception {
        Role removedRole;
        synchronized (m_roleMap) {
            removedRole = m_roleMap.remove(name);
            if (removedRole != null){
                roleChanged(null);
            }
        }
        return removedRole;
    }

    List<String> memberOf(Role role) {
        List<String> memberOf = new ArrayList<>();
        for (Role r: m_roleMap.values()) {
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
    
    /**
     * Helper method that checks the presence of an object in an array. Returns <code>true</code> if <code>t</code> is
     * in <code>ts</code>, <code>false</code> otherwise.
     */
    private <T> boolean contains(T t, T[] ts) {
        for (T current : ts) {
            if (current.equals(t)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void roleChanged(UserAdminEvent event) {
        synchronized (m_roleMap) {
            XStream instance = XStreamFactory.getInstance();
            try (StringWriter writer = new StringWriter();
                            ObjectOutputStream stream =
                                instance.createObjectOutputStream(writer, "roles");) {
                
                for (Role role : m_roleMap.values()) {
                    List<String> memberOf = memberOf(role);
                    if (role.getType() == Role.USER) {
                        stream.writeObject(new UserDTO((User) role, memberOf));
                    } else if (role.getType() == Role.GROUP) {
                        GroupDTO obj = new GroupDTO((Group) role, memberOf);
                        stream.writeObject(obj);
                    } else {
                        throw new IllegalStateException("Unsupported role type");
                    }
                }
    
                stream.flush();
                stream.close();
                writer.flush();
                
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(writer.toString().getBytes())){
                    m_cachedRepository.writeLocal(inputStream);
                }
                
                m_cachedRepository.commit();
                m_version.set(m_cachedRepository.getMostRecentVersion());
            } catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Failed to commit role changes to the main role repository", e);
            } 
        }
    }
    
}
