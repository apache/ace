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

import java.io.ByteArrayInputStream;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
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

    private volatile UserEditor m_userEditor;
    private volatile Repository m_userRepository;
    private volatile UserAdmin m_userAdmin;
    private boolean m_hasBeenSetup = false;

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
            .add(createServiceDependency()
                .setService(Repository.class, "(&(name=users)(customer=apache))")
                .setRequired(true))
        };
    }

    @Override
    protected void configureProvisionedServices() throws Exception {

        if (m_hasBeenSetup)
            return;

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "users",
            "customer", "apache",
            "master", "true");
        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
            "repositoryName", "users",
            "repositoryCustomer", "apache");
        configure("org.apache.ace.scheduler",
            "org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", "100");
    }

    @Override
    protected void configureAdditionalServices() throws Exception {

        if (m_hasBeenSetup)
            return;

        m_hasBeenSetup = true;
        ByteArrayInputStream bis = new ByteArrayInputStream((
            "<roles>" +
                "    <group name=\"TestGroup\">" +
                "        <properties>" +
                "            <type>userGroup</type>" +
                "        </properties>" +
                "    </group>" +
                "    <user name=\"TestUser\">" +
                "        <properties>" +
                "            <email>testUser@apache.org</email>" +
                "        </properties>" +
                "        <credentials>" +
                "            <password type=\"String\">swordfish</password>" +
                "            <certificate type=\"byte[]\">42</certificate>" +
                "        </credentials>" +
                "        <memberof>TestGroup</memberof>" +
                "    </user>" +
                "</roles>").getBytes());

        assertTrue("Committing test user data failed.", m_userRepository.commit(bis, m_userRepository.getRange().getHigh()));
        User user = (User) m_userAdmin.getRole("TestUser");
        int count = 0;
        while ((user == null) && (count < 60)) {
            Thread.sleep(100);
            user = (User) m_userAdmin.getRole("TestUser");
            count++;
        }
        assertNotNull("Failed to load the user", user);
    }

    public void testGetGroups() {
        assertEquals(1, m_userEditor.getGroups().size());
    }

    public void testGetUsers() {
        assertEquals(1, m_userEditor.getData().size());
    }

    public void testGetGroupByUser() {
        User newUser = null;
        Role newRole = m_userAdmin.createRole((String) "Testuser", Role.USER);
        Group group = (Group) m_userAdmin.getRole("TestGroup");
        if (newRole != null && group != null) {
            newUser = (User) newRole;
            newUser.getProperties().put("username", "u");
            newUser.getCredentials().put("password", "p");
            group.addMember(newUser);
        }
        assertEquals(group, m_userEditor.getGroup(newUser));
        m_userAdmin.removeRole("u");
    }

    public void testGetGroupByUserNull() {
        assertNull(m_userEditor.getGroup(null));
    }

    public void testAddUserAndRemove() throws Exception {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");
        m_userEditor.addUser(userDTO);
        User user = m_userEditor.getUser("tran");
        assertEquals("tran", (String) user.getProperties().get("username"));
        m_userEditor.removeUser(userDTO);
    }

    public void testAddUserWithEmptyUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("", "tran", "TestGroup");
            m_userEditor.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserWithNullUsername() throws Exception {
        try {
            UserDTO userDTO = new UserDTO(null, "tran", "TestGroup");
            m_userEditor.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserToNonExistingGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", "nonexistingGroup");
            m_userEditor.addUser(userDTO);
        }
        catch (Exception ex) {
            assertEquals("Group: nonexistingGroup not found", ex.getMessage());
        }
    }

    public void testAddUserToNullGroup() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", null);
            m_userEditor.addUser(userDTO);
        }
        catch (IllegalArgumentException ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testAddUserToEmptyGroupname() throws Exception {
        try {
            UserDTO userDTO = new UserDTO("tran", "bob", "");
            m_userEditor.addUser(userDTO);
        }
        catch (Exception ex) {
            assertEquals("Username, password and groupname cannot be null or \"\"", ex.getMessage());
        }
    }

    public void testEditUserWithValidPassword() throws Exception {
        UserDTO userDTO = new UserDTO("bob", "tran", "TestGroup");
        m_userEditor.addUser(userDTO);
        userDTO.setPassword("bob");
        m_userEditor.editPassword(userDTO);
        assertEquals("bob", (String) m_userEditor.getUser("bob").getCredentials().get("password"));
        m_userEditor.removeUser(userDTO);
    }

    public void testEditUserWithNullPassword() throws UserNotFoundException {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");

        try {
            m_userEditor.addUser(userDTO);
            userDTO.setPassword(null);
            m_userEditor.editPassword(userDTO);
        }
        catch (Exception e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
            m_userEditor.removeUser(userDTO);
        }
    }

    public void testEditUserWithEmptyPassword() throws UserNotFoundException {
        UserDTO userDTO = new UserDTO("tran", "tran", "TestGroup");
        try {

            m_userEditor.addUser(userDTO);
            userDTO.setPassword("");
            m_userEditor.editPassword(userDTO);
        }
        catch (Exception e) {
            assertEquals("Username or Password cannot be null or \"\" ", e.getMessage());
            m_userEditor.removeUser(userDTO);
        }
    }

    public void testEditNonExistingUser() {
        try {
            UserDTO userDTO = new UserDTO("BOOOOOB", null, null);
            userDTO.setUsername("bob");
            m_userEditor.editUsername(userDTO);
        }
        catch (Exception userNotFoundException) {
            assertEquals("User: BOOOOOB not found", userNotFoundException.getMessage());
        }
    }

    public void testEditUsernameWithValidName() throws Exception {
        UserDTO userDTO = new UserDTO("lala", "tran", "TestGroup");
        m_userEditor.addUser(userDTO);
        m_userAdmin.getUser("username", "lala").getProperties().put("username", "lala1");
        User user = (User) m_userAdmin.getRole("lala");
        assertEquals("lala", user.getName());
        assertEquals("lala1", (String) user.getProperties().get("username"));
        user = m_userAdmin.getUser("username", "lala1");
        userDTO = new UserDTO(user, m_userEditor.getGroup(user));
        assertEquals("lala", user.getName());
        assertEquals("lala1", (String) user.getProperties().get("username"));
        m_userEditor.removeUser(userDTO);
    }

    public void testEditUsernameWithAlreadyExistingName() throws UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Hank", "password", "TestGroup");
            m_userEditor.addUser(userDTO);
            userDTO = new UserDTO("Dirk", "password", "TestGroup");
            m_userEditor.addUser(userDTO);
            userDTO.setUsername("Hank");
            m_userEditor.editUsername(userDTO);
        }
        catch (Exception userAlreadyExistsException) {
            assertEquals("User: Hank already exists", userAlreadyExistsException.getMessage());
            m_userEditor.removeUser(new UserDTO("Hank", null, null));
            m_userEditor.removeUser(new UserDTO("Dirk", null, null));
        }
    }

    public void testEditUserNameWithNull() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Dirk", "password", "TestGroup");
            m_userEditor.addUser(userDTO);
            userDTO.setUsername(null);
            m_userEditor.editUsername(userDTO);
        }
        catch (Exception invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
            m_userEditor.removeUser(new UserDTO("Dirk", "password", "TestGroup"));
        }
    }

    public void testEditUserNameWithEmptyName() throws GroupNotFoundException, UserAlreadyExistsException, UserNotFoundException {
        try {
            UserDTO userDTO = new UserDTO("Dirk", "password", "TestGroup");
            m_userEditor.addUser(userDTO);
            userDTO.setUsername("");
            m_userEditor.editUsername(userDTO);
        }
        catch (Exception invalidArgumentException) {
            assertEquals("oldUsername and newUsername cannot be null or \"\" ", invalidArgumentException.getMessage());
            m_userEditor.removeUser(new UserDTO("Dirk", "password", "TestGroup"));
        }
    }

    public void GetUserBroken() {
        User newUser = null;
        Role newRole = m_userAdmin.createRole((String) "Testuser", Role.USER);
        Group group = (Group) m_userAdmin.getRole("TestGroup");
        if (newRole != null && group != null) {
            newUser = (User) newRole;
            newUser.getProperties().put("username", "u");
            newUser.getCredentials().put("password", "p");
            group.addMember(newUser);
        }
        assertEquals("Testuser", m_userEditor.getUser("u").getName());
    }
}
