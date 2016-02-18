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

import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.User;

/**
 * UserDTO contains the information of the user and the group.
 */
public class UserDTO implements Comparable<UserDTO> {
    private String m_previousUsername;
    private String m_username;
    private String m_password;
    private String m_previousGroupname;
    private String m_groupname;
    private boolean m_usernameChanged;
    private boolean m_passwordChanged;
    private boolean m_groupChanged;

    public UserDTO(User user, Group group) {
        m_username = (String) user.getProperties().get("username");
        m_previousUsername = m_username;
        m_password = (String) user.getCredentials().get("password");
        m_groupname = group != null? group.getName(): null;
        m_previousGroupname = m_groupname;
    }

    public UserDTO(String username, String password, String groupname) {
        m_username = username;
        m_password = password;
        m_previousUsername = username;
        m_groupname = groupname;
        m_previousGroupname = groupname;
    }

    /**
     * Sets the username changed flag.
     * 
     * @param usernameChanged
     */
    public void setUsernameChanged(boolean usernameChanged) {
        m_usernameChanged = usernameChanged;
    }

    /**
     * Sets the password changed flag.
     * 
     * @param passwordChanged
     */
    public void setPasswordChanged(boolean passwordChanged) {
        m_passwordChanged = passwordChanged;
    }

    /**
     * Sets the group changed flag.
     * 
     * @param groupChanged
     */
    public void setGroupChanged(boolean groupChanged) {
        m_groupChanged = groupChanged;
    }

    /**
     * Returns the state of username.
     * 
     * @return true when the username is changed and will return false when username isn't changed
     */
    public boolean isUsernameChanged() {
        return m_usernameChanged;
    }

    /**
     * Returns the state of password.
     * 
     * @return true when the password is changed and will return false when password isn't changed
     */
    public boolean isPasswordChanged() {
        return m_passwordChanged;
    }

    /**
     * Returns the state of group.
     * 
     * @return true when the group is changed and will return false when group isn't changed
     */
    public boolean isGroupChanged() {
        return m_groupChanged;
    }

    /**
     * @return the previousGroupname
     */
    public String getPreviousGroupname() {
        return m_previousGroupname;
    }
    
    /**
     * Returns the current username that is stored in the useradminService.
     * 
     * @return username
     */
    public String getPreviousUsername() {
        return m_previousUsername;
    }

    /**
     * Returns the current username. This can be either the one that is stored in userAdminService, or the
     * current username that is store in this object.
     * 
     * @return username
     */
    public String getUsername() {
        return m_username;
    }

    /**
     * Set the current username.
     * 
     * @param username
     */
    public void setUsername(String username) {
        m_usernameChanged = true;
        m_previousUsername = m_username;
        m_username = username;
    }

    /**
     * Returns the current password.
     * 
     * @return password
     */
    public String getPassword() {
        return m_password;
    }

    /**
     * Changes the current password.
     * 
     * @param password
     */
    public void setPassword(String password) {
        m_passwordChanged = true;
        m_password = password;
    }

    /**
     * Returns the current groupname.
     * 
     * @return
     */
    public String getGroupname() {
        return m_groupname;
    }

    /**
     * Sets the current groupname.
     * 
     * @param groupname
     */
    public void setGroupname(String groupname) {
        m_groupChanged = true;
        m_previousGroupname = m_groupname;
        m_groupname = groupname;
    }

    public String toString() {
        return m_username;
    }

    @Override
    public int compareTo(UserDTO other) {
        return m_username.compareTo(other.m_username);
    }

    /**
     * 
     * @return true when if username,password, or group is changed
     */
    public boolean isUpdated() {
        if (m_usernameChanged || m_passwordChanged || m_groupChanged) {
            return true;
        }
        return false;
    }
}
