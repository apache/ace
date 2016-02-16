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

    private static final String TEST_GROUP = "TestGroup";

    private volatile UserEditor m_userEditor;
    private volatile UserAdmin m_userAdmin;

    public void GetUserBroken() {
        User newUser = null;
        Role newRole = m_userAdmin.createRole((String) "Testuser", Role.USER);
        Group group = (Group) m_userAdmin.getRole(TEST_GROUP);
        if (newRole != null && group != null) {
            newUser = (User) newRole;
            newUser.getProperties().put("username", "u");
            newUser.getCredentials().put("password", "p");
            group.addMember(newUser);
        }
        assertEquals("Testuser", m_userEditor.getUser("u").getName());
    }

    public void testAddUserAndRemove() throws Exception {
        String username = "name";

        UserDTO userDTO = new UserDTO(username, "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        User user = m_userEditor.getUser(username);
        assertNotNull(user);
        assertEquals(username, (String) user.getProperties().get("username"));
    }

    public void testAddUserToEmptyGroupname() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("name", "pwd", "");
            m_userEditor.addUser(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserToNonExistingGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("name", "pwd", "nonexistingGroup");
            m_userEditor.addUser(userDTO);

            fail("Expected GroupNotFoundException!");
        }
        catch (GroupNotFoundException ex) {
            assertEquals("Group: nonexistingGroup not found", ex.getMessage());
        }
    }

    public void testAddUserToNullGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("user", "pwd", null);
            m_userEditor.addUser(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserWithEmptyUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("", "pwd", TEST_GROUP);
            m_userEditor.addUser(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserWithNullUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO(null, "pwd", TEST_GROUP);
            m_userEditor.addUser(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testEditNonExistingUser() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("nonExistingUser", null, null);
            userDTO.setUsername("anotherName");

            m_userEditor.editUsername(userDTO);

            fail("Expected UserNotFoundException!");
        }
        catch (UserNotFoundException userNotFoundException) {
            assertEquals("User: nonExistingUser not found", userNotFoundException.getMessage());
        }
    }

    public void testEditUsernameWithAlreadyExistingName() throws Exception {
        UserDTO userDTO = new UserDTO("user1", "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        userDTO = new UserDTO("user2", "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        try {
            userDTO.setUsername("user1");
            m_userEditor.editUsername(userDTO);

            fail("Expected UserAlreadyExistsException!");
        }
        catch (UserAlreadyExistsException userAlreadyExistsException) {
            assertEquals("User: user1 already exists", userAlreadyExistsException.getMessage());
        }
    }

    public void testEditUserNameWithEmptyName() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        UserDTO userDTO = new UserDTO("user", "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        try {
            userDTO.setUsername("");
            m_userEditor.editUsername(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
        }
    }

    public void testEditUserNameWithNull() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        UserDTO userDTO = new UserDTO("user", "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        try {
            userDTO.setUsername(null);
            m_userEditor.editUsername(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
        }
    }

    public void testEditUsernameWithValidName() throws Exception {
        String username = "user1";

        UserDTO userDTO = new UserDTO(username, "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        m_userAdmin.getUser("username", username).getProperties().put("username", "user2");

        User user = (User) m_userAdmin.getRole(username);
        assertEquals(username, user.getName());
        assertEquals("user2", (String) user.getProperties().get("username"));

        user = m_userAdmin.getUser("username", "user2");
        userDTO = new UserDTO(user, m_userEditor.getGroup(user));
        assertEquals(username, user.getName());
        assertEquals("user2", (String) user.getProperties().get("username"));
    }

    public void testEditUserWithEmptyPassword() throws Exception {
        UserDTO userDTO = new UserDTO("tran", "tran", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        try {
            userDTO.setPassword("");
            m_userEditor.editPassword(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
        }
    }

    public void testEditUserWithNullPassword() throws Exception {
        UserDTO userDTO = new UserDTO("tran", "tran", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        try {
            userDTO.setPassword(null);
            m_userEditor.editPassword(userDTO);

            fail("Expected IllegalArgumentException!");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
        }
    }

    public void testEditUserWithValidPassword() throws Exception {
        String username = "user1";

        UserDTO userDTO = new UserDTO(username, "pwd", TEST_GROUP);
        m_userEditor.addUser(userDTO);

        userDTO.setPassword(username);
        m_userEditor.editPassword(userDTO);

        assertEquals(username, (String) m_userEditor.getUser(username).getCredentials().get("password"));
    }

    public void testGetGroupByUser() {
        Role newRole = m_userAdmin.createRole((String) "Testuser", Role.USER);
        assertNotNull(newRole);

        Group group = (Group) m_userAdmin.getRole(TEST_GROUP);
        assertNotNull(group);

        User newUser = (User) newRole;
        newUser.getProperties().put("username", "u");
        newUser.getCredentials().put("password", "p");
        group.addMember(newUser);

        Group userGroup = m_userEditor.getGroup(newUser);
        assertNotNull(userGroup);
        assertEquals(group.getName(), userGroup.getName());
    }

    public void testGetGroupByUserNull() {
        assertNull(m_userEditor.getGroup(null));
    }

    public void testGetGroups() {
        assertEquals(1, m_userEditor.getGroups().size());
    }

    public void testGetUsers() {
        assertEquals(1, m_userEditor.getData().size());
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        Group group = (Group) m_userAdmin.createRole(TEST_GROUP, Role.GROUP);
        group.getProperties().put("type", "userGroup");

        User user = (User) m_userAdmin.createRole("TestUser", Role.USER);
        user.getProperties().put("email", "testUser@apache.org");
        user.getCredentials().put("password", "swordfish");
        user.getCredentials().put("certificate", "42".getBytes());

        group.addMember(user);
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        configureFactory("org.apache.ace.server.repository.factory",
            "name", "users",
            "customer", "apache",
            "master", "true");
        
    }

    @Override
    protected void doTearDown() throws Exception {
        Role[] roles = m_userAdmin.getRoles(null);
        for (Role role : roles) {
            try {
                m_userAdmin.removeRole(role.getName());
            }
            catch (Exception exception) {
                // Ignore...
            }
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
}
