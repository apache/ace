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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import org.apache.ace.test.utils.TestUtils;
import org.apache.felix.framework.FilterImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RepositoryUserAdminTest {

    private RepositoryUserAdminImpl m_impl;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_impl = new RepositoryUserAdminImpl();
        TestUtils.configureObject(m_impl, BundleContext.class, TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public Filter createFilter(String s) throws InvalidSyntaxException {
                return new FilterImpl(s);
            }
        }));
    }

    /**
     * Tests basic creation and membership of groups.
     */
    @Test(groups = { UNIT })
    public void testCreation() {
        User user = (User) m_impl.createRole("me", Role.USER);
        Group group = (Group) m_impl.createRole("myGroup", Role.GROUP);
        group.addMember(user);
        assert group.getMembers().length == 1 : "We expect to find one member, not " + group.getMembers().length;
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testUserProperties() {
        User user = (User) m_impl.createRole("me", Role.USER);
        user.getProperties().put("fullname", "Mr. M. Me");
        assert m_impl.getUser("fullname", "Mr. M. Me").equals(user);

        Group group = (Group) m_impl.createRole("theGroup", Role.GROUP);
        assert m_impl.getUser("fullname", "Mr. M. Me").equals(user); // We should not find the group we just created

        m_impl.removeRole("me");
        assert m_impl.getUser("fullname", "Mr. M. Me") == null; // We should not find the group we just created
    }

    @Test(groups = { UNIT })
    public void testGetRoles() throws InvalidSyntaxException {
        User user = (User) m_impl.createRole("me", Role.USER);
        user.getProperties().put("fullname", "Mr. M. Me");
        Group group = (Group) m_impl.createRole("myGroup", Role.GROUP);
        Role[] roles = m_impl.getRoles(null);
        assert roles.length == 2;
        roles = m_impl.getRoles("(fullname=Mr. M. Me)");
        assert roles.length == 1;
        roles = m_impl.getRoles("(fullname=Mr. U. Me)");
        assert roles == null; // Spec requires us to return null in stead of an empty array
    }

    @Test(groups = { UNIT })
    public void testCreateDoubleRole() throws InvalidSyntaxException {
        User user = (User) m_impl.createRole("test", Role.USER);
        Group group = (Group) m_impl.createRole("test", Role.GROUP);
        assert group == null;
        assert m_impl.getRole("test").equals(user);
        assert m_impl.getRoles(null).length == 1;
    }

    @Test(groups = { UNIT })
    public void testCredentials() throws InvalidSyntaxException {
        User user = (User) m_impl.createRole("me", Role.USER);
        user.getCredentials().put("password", "swordfish");
        assert user.hasCredential("password", "swordfish");
        assert !user.hasCredential("pet", "swordfish");
        assert !user.hasCredential("password", "barracuda");
    }

    @Test(groups = { UNIT })
    public void testStringOnlyDictionary() throws InvalidSyntaxException {
        User user = (User) m_impl.createRole("me", Role.USER);
        try {
            user.getProperties().put("clearanceLevel", new Integer(5));
            assert false : "Only String or byte[] values should be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        try {
            user.getProperties().put("clearanceLevel", '5');
            assert false : "Only String or byte[] values should be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        try {
            user.getProperties().put("clearanceLevel", "5");
        }
        catch (IllegalArgumentException iae) {
            assert false : "String values should be allowed.";
        }

        try {
            user.getProperties().put("clearanceLevel", new byte[] {'5'});
        }
        catch (IllegalArgumentException iae) {
            assert false : "byte[] values should be allowed.";
        }

        try {
            user.getProperties().put(new String[] {"clearanceLevel"}, "5");
            assert false : "String[] keys should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        try {
            user.getProperties().put(new byte[] {'c','l','e','a','r','a','n','c','e','L','e','v','e','l'}, "5");
            assert false : "byte[] keys should not be allowed.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }
    }
}
