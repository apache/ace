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
package org.apache.ace.useradmin.ui.test;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.useradmin.ui.editor.GroupNotFoundException;
import org.apache.ace.useradmin.ui.editor.UserAlreadyExistsException;
import org.apache.ace.useradmin.ui.editor.UserDTO;
import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.apache.ace.useradmin.ui.editor.UserNotFoundException;
import org.apache.felix.dm.Component;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

public class UserEditorTest extends IntegrationTestBase {
    private volatile UserEditor instance;
    private volatile UserAdmin useradmin;
    private static boolean firststart = true;

    public UserEditorTest() {
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        super.configureProvisionedServices();
        if (firststart) {
            Thread.sleep(1000);
            firststart = false;
        }
    }

    protected org.apache.felix.dm.Component[] getDependencies() {
        return new Component[] { createComponent()
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(UserEditor.class)
                .setRequired(true)
            )
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true)
            )
        };
    }
    
    // How to test in future? When amount of groups grows, test will fail...
    // Need something to assert here.
    public void testGetGroups() {
        assertEquals(6, instance.getGroups().size());
    }

    // How to test in future? When amount of users grows, test will fail...
    // Need something to assert here.
    public void testGetUsers() {
        assertEquals(6, instance.getData().size());
    }

    public void testGetGroupByUser() {
        User newUser = null;
        Role newRole = useradmin.createRole((String) "Testuser", Role.USER);
        Group group = (Group) useradmin.getRole("TestGroup");
        if (newRole != null && group != null) {
            newUser = (User) newRole;
            newUser.getProperties().put("username", "u");
            newUser.getCredentials().put("password", "p");
            group.addMember(newUser);
        }
        assertEquals(group, instance.getGroup(newUser));
        useradmin.removeRole("u");
    }

    public void testGetGroupByUserNull() {
        assertNull(instance.getGroup(null));
    }

    public void testAddUserAndRemove() throws Exception {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");
        instance.addUser(userDTO);
        User user = instance.getUser("tran");
        assertEquals("tran", (String) user.getProperties().get("username"));
        instance.removeUser(userDTO);
    }

    public void testAddUserWithEmptyUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("", "tran", "TestGroup");
            instance.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }

    }

    public void testAddUserWithNullUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO(null, "tran", "TestGroup");
            instance.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }

    }

    public void testAddUserToNonExistingGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", "nonexistingGroup");
            instance.addUser(userDTO);
        }
        catch (Exception ex) {
            assertEquals("Group: nonexistingGroup not found", ex.getMessage());
        }
    }

    public void testAddUserToNullGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", null);
            instance.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserToEmptyGroupname() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", "");
            instance.addUser(userDTO);
        }
        catch (Exception ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testEditUserWithValidPassword() throws Exception {
        UserDTO userDTO = new UserDTO("bob", "tran", "TestGroup");
        instance.addUser(userDTO);
        userDTO.setPassword("bob");
        instance.editPassword(userDTO);
        assertEquals("bob", (String) instance.getUser("bob").getCredentials().get("password"));
        instance.removeUser(userDTO);
    }

    public void testEditUserWithNullPassword() throws UserNotFoundException {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");

        try {
            instance.addUser(userDTO);
            userDTO.setPassword(null);
            instance.editPassword(userDTO);
        }
        catch (Exception e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
            instance.removeUser(userDTO);
        }
    }

    public void testEditUserWithEmptyPassword() throws UserNotFoundException {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");
        try {

            instance.addUser(userDTO);
            userDTO.setPassword("");
            instance.editPassword(userDTO);
        }
        catch (Exception e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
            instance.removeUser(userDTO);
        }
    }

    public void testEditNonExistingUser() {
        try {
            UserDTO userDTO = new UserDTO("BOOOOOB", null, null);
            userDTO.setUsername("bob");
            instance.editUsername(userDTO);
        }
        catch (Exception userNotFoundException) {
            assertEquals("User: BOOOOOB not found", userNotFoundException.getMessage());
        }
    }

    public void testEditUsernameWithValidName() throws Exception {
        UserDTO userDTO = new UserDTO("lala", "tran", "TestGroup");
        instance.addUser(userDTO);
        useradmin.getUser("username", "lala").getProperties().put("username", "lala1");
        User user = (User) useradmin.getRole("lala");
        assertEquals("lala", user.getName());
        assertEquals("lala1", (String) user.getProperties().get("username"));
        user = useradmin.getUser("username", "lala1");
        userDTO = new UserDTO(user, instance.getGroup(user));
        assertEquals("lala", user.getName());
        assertEquals("lala1", (String) user.getProperties().get("username"));
        instance.removeUser(userDTO);
    }

    public void testEditUsernameWithAlreadyExistingName() throws UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Hank", "password", "TestGroup");
            instance.addUser(userDTO);
            userDTO = new UserDTO("Dirk", "password", "TestGroup");
            instance.addUser(userDTO);
            userDTO.setUsername("Hank");
            instance.editUsername(userDTO);
        }
        catch (Exception userAlreadyExistsException) {
            assertEquals("User: Hank already exists", userAlreadyExistsException.getMessage());
            instance.removeUser(new UserDTO("Hank", null, null));
            instance.removeUser(new UserDTO("Dirk", null, null));
        }
    }

    public void testEditUserNameWithNull() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Dirk", "password", "TestGroup");
            instance.addUser(userDTO);
            userDTO.setUsername(null);
            instance.editUsername(userDTO);
        }
        catch (Exception invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
            instance.removeUser(new UserDTO("Dirk", "password", "TestGroup"));
        }
    }

    public void testEditUserNameWithEmptyName() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Dirk", "password", "TestGroup");
            instance.addUser(userDTO);
            userDTO.setUsername("");
            instance.editUsername(userDTO);
        }
        catch (Exception invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
            instance.removeUser(new UserDTO("Dirk", "password", "TestGroup"));
        }
    }

    // // Broken test, newUser is null at end of method??
    // public void GetUserBroken() {
    // User newUser = null;
    // Role newRole = useradmin.createRole((String) "Testuser", Role.USER);
    // Group group = (Group) useradmin.getRole("TestGroup");
    // if (newRole != null && group != null)
    // {
    // newUser = (User) newRole;
    // newUser.getProperties().put("username", "u");
    // newUser.getCredentials().put("password", "p");
    // group.addMember(newUser);
    // }
    // assertEquals("Testuser", instance.getUser("u").getName());
    // }

}
