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
package org.apache.ace.useradmin.ui.editor;

import java.util.List;

import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.User;

/**
 * UserEditor is used to call operations to edit Users on the useradminService
 */
public interface UserEditor {
    /**
     * AddUser can be called to ask the useradmin to add an user to useradmin service If the user already exist the
     * method will throw an exception If the useradmin can't find the group with the groupname, the editor will throw an
     * exception
     * 
     * @param userDTO userDTO object contains the information a user.
     * @throws UserAlreadyExistsException Will be thrown when a user with the username already exist
     * @throws GroupNotFoundException Will be thrown when the group isn't found
     */
    void addUser(UserDTO userDTO) throws GroupNotFoundException, UserAlreadyExistsException;

    /**
     * Will check if the userDTO has changed
     * 
     * @param userDTO userDTO object contains the information of a user.
     * @throws UserNotFoundException Will be thrown when the old user cannot be found
     * @throws GroupNotFoundException Will be thrown when the new group can't be found
     * @throws UserAlreadyExistsException Will be thrown when a user with the username already exist
     */
    void updateUser(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException, UserAlreadyExistsException;

    /**
     * editUser can be called to edit the username of an user. It will add a new Role with the information of the old
     * Role. Then it will remove the old Role and it will remove the user from the group where the user is belonging to.
     * 
     * @throws UserNotFoundException Will be thrown when a user isn't found
     * @throws GroupNotFoundException Will be thrown when the group isn't found
     */
    void editUsername(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException, UserAlreadyExistsException;

    /**
     * Can be used to edit the password of a user
     * 
     * @param userDTO userDTO object contains the information of a user.
     * @throws UserNotFoundException Will be thrown when a user isn't found
     * 
     */
    void editPassword(UserDTO userDTO) throws UserNotFoundException;

    /**
     * Can be used to modify the group of a user;
     * 
     * @param userDTO userDTO object contains the information of a user.
     * @throws UserAlreadyExistsException Will be thrown when a user with the username already exist
     * @throws UserNotFoundException Will be thrown when a user isn't found
     * @throws GroupNotFoundException Will be thrown when the group isn't found
     */
    void editGroup(UserDTO userDTO) throws UserNotFoundException, GroupNotFoundException;

    /**
     * removeUser will ask the useradmin to remove a user. It will also check if the user is belonging to any group. If
     * the user is a member of the group. It will then remove the user of the specific group
     * 
     * @param userDTO userDTO object contains the information a user.
     * @throws UserNotFoundException Will be thrown when a user isn't found
     * 
     */
    void removeUser(UserDTO userDTO) throws UserNotFoundException;

    /**
     * Returns all currently known users from the UserAdmin service.
     * 
     * @return List of currently known users in UserAdmin
     */
    List<UserDTO> getData();

    /**
     * Returns all currently known groups from the UserAdmin service.
     * 
     * @return List of currently known groups in UserAdmin
     */
    List<Group> getGroups();

    /**
     * Returns the specific Group to which the given User belongs.
     * 
     * @param user
     * @return Group to which the given user belongs.
     */
    Group getGroup(User user);

    /**
     * Returns the User tied to the given username.
     * 
     * @param username
     * @return User tied to given username.
     */
    User getUser(String username);

    /**
     * Returns a list from Users
     * 
     * @return list users
     */
    List<User> getUsers();

    /**
     * Will check if the user got the roles
     * 
     * @return
     */
    boolean hasRole(User user, String role);
}
