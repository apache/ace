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
package org.apache.ace.useradmin.ui.editor.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.ace.useradmin.ui.editor.GroupNotFoundException;
import org.apache.ace.useradmin.ui.editor.UserAlreadyExistsException;
import org.apache.ace.useradmin.ui.editor.UserDTO;
import org.apache.ace.useradmin.ui.editor.UserNotFoundException;
import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * An implementation of UserEditor. Can be used to execute operations on users in the useradminService;
 */
public class UserEditorImpl implements UserEditor {
    // Injected by Felix DM...
    private volatile UserAdmin m_useradmin;

    @Override
    public void addUser(UserDTO userDTO) throws GroupNotFoundException, UserAlreadyExistsException {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        String groupname = userDTO.getGroupname();

        if (username == null || "".equals(username) || password == null || "".equals(password) || groupname == null || "".equals(groupname)) {
            throw new IllegalArgumentException("Username, password and groupname cannot be null or \"\"");
        }

        Group group = (Group) m_useradmin.getRole(groupname);
        if (group == null) {
            throw new GroupNotFoundException(groupname);
        }

        Role newRole = m_useradmin.createRole(username, Role.USER);
        if (newRole == null) {
            throw new UserAlreadyExistsException(username);
        }

        User newUser = (User) newRole;
        newUser.getProperties().put("username", username);
        newUser.getCredentials().put("password", password);
        group.addMember(newUser);
    }

    @Override
    public void editGroup(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException {
        String username = userDTO.getUsername();
        String group = userDTO.getGroupname();

        if (username == null || group == null) {
            throw new IllegalArgumentException("Username and group cannot be null or \"\" ");
        }

        User user = getUser(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }

        Group oldGroup = (Group) m_useradmin.getRole(userDTO.getPreviousGroupname());
        if (oldGroup == null) {
            throw new GroupNotFoundException(group);
        }

        Group newGroup = (Group) m_useradmin.getRole(group);
        if (newGroup == null) {
            throw new GroupNotFoundException(group);
        }

        oldGroup.removeMember(user);
        newGroup.addMember(user);
    }

    @Override
    public void editPassword(UserDTO userDTO) throws UserNotFoundException {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();

        if (username == null || password == null || "".equals(password)) {
            throw new IllegalArgumentException("Username or Password cannot be null or \"\" ");
        }

        User user = getUser(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }

        user.getCredentials().put("password", password);
    }

    @Override
    public void editUsername(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException, UserAlreadyExistsException {
        String oldUsername = userDTO.getPreviousUsername();
        String newUsername = userDTO.getUsername();

        if (oldUsername == null || newUsername == null || "".equals(newUsername)) {
            throw new IllegalArgumentException("oldUsername and newUsername cannot be null or \"\" ");
        }
        if (newUsername.equals(oldUsername)) {
            // Nothing needs to be done...
            return;
        }
        if (getUser(newUsername) != null) {
            throw new UserAlreadyExistsException(newUsername);
        }
        User user = getUser(oldUsername);
        if (user == null) {
            throw new UserNotFoundException(oldUsername);
        }
        Group group = getGroup(user);
        if (group == null) {
            throw new GroupNotFoundException(null);
        }

        group.removeMember(user);
        m_useradmin.removeRole(user.getName());

        User newUser = (User) m_useradmin.createRole(newUsername, Role.USER);
        newUser.getProperties().put("username", newUsername);
        newUser.getCredentials().put("password", userDTO.getPassword());

        group.addMember(newUser);
    }

    @Override
    public List<UserDTO> getData() {
        List<UserDTO> tempData = new ArrayList<>();
        try {
            Role[] roles = m_useradmin.getRoles(null);
            if (roles != null) {
                for (Role role : roles) {
                    if (role.getType() == Role.USER) {
                        User user = (User) role;
                        tempData.add(new UserDTO((User) role, getGroup(user)));
                    }
                }
            }
        }
        catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
        return tempData;
    }

    @Override
    public Group getGroup(User user) {
        Authorization auth = m_useradmin.getAuthorization(user);
        String[] roles = auth.getRoles();
        if (roles != null) {
            for (String role : roles) {
                Role result = m_useradmin.getRole(role);
                if (result.getType() == Role.GROUP) {
                    Group group = (Group) result;
                    Role[] members = group.getMembers();
                    if (members != null) {
                        for (Role r : members) {
                            if (r.getType() == Role.USER && r.getName().equals(user.getName())) {
                                return group;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<Group> getGroups() {
        List<Group> tempGroups = new ArrayList<>();
        try {
            Role[] roles = m_useradmin.getRoles("(type=userGroup)");
            if (roles != null) {
                for (Role role : roles) {
                    if (role.getType() == Role.GROUP) {
                        tempGroups.add((Group) role);
                    }
                }
            }
        }
        catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
        return tempGroups;
    }

    @Override
    public User getUser(String username) {
        return m_useradmin.getUser("username", username);
    }

    @Override
    public List<User> getUsers() {
        List<User> tempUsers = new ArrayList<>();
        try {
            Role[] roles = m_useradmin.getRoles(null);
            if (roles != null) {
                for (Role role : roles) {
                    if (role.getType() == Role.USER) {
                        tempUsers.add((User) role);
                    }
                }
            }
        }
        catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
        return tempUsers;
    }

    @Override
    public boolean hasRole(User user, String role) {
        Authorization authorization = m_useradmin.getAuthorization(user);
        return authorization.hasRole(role);
    }

    @Override
    public void removeUser(UserDTO userDTO) throws UserNotFoundException {
        String username = userDTO.getUsername();
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null or \"\" ");
        }
        User user = getUser(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        Group group = getGroup(user);
        group.removeMember(user);
        m_useradmin.removeRole(user.getName());
    }

    public void updateUser(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException, UserAlreadyExistsException {
        if (userDTO.isUsernameChanged()) {
            editUsername(userDTO);
        }
        else {
            if (userDTO.isPasswordChanged()) {
                editPassword(userDTO);
            }
            if (userDTO.isGroupChanged()) {
                editGroup(userDTO);
            }
        }
    }
}
