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
package org.apache.ace.useradmin.ui.vaadin;

import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.useradmin.User;

import com.vaadin.ui.Button;
import com.vaadin.ui.Window;

public class UserAdminButton extends Button {

    private final User m_currentUser;
    private volatile UserEditor m_userUtil;
    private volatile Component m_window;
    private volatile ClickListener m_click;

    public UserAdminButton(User currentUser) {
        m_currentUser = currentUser;
        setEnabled(false);
    }

    public void init(Component component) {
        final DependencyManager dm = component.getDependencyManager();
        setEnabled(true);
        if (m_userUtil.hasRole(m_currentUser, "editUsers")) {
            setCaption("Manage Users");
        }
        else {
            setCaption("My Info");
        }
        final Window window = new UserAdminWindow(m_currentUser);
        window.setModal(true);
        // create a new dependency for the window
        m_window = dm.createComponent()
            .setImplementation(window)
            .add(dm.createServiceDependency()
                .setService(UserEditor.class)
                .setRequired(true)
            );
        dm.add(m_window);
        m_click = new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                getWindow().addWindow(window);
            }
        };
        addListener(m_click);
    }

    public void destroy(Component component) {
        setEnabled(false);
        component.getDependencyManager().remove(m_window);
        removeListener(m_click);
        setDescription("This service seems to be unavailable at this moment...");
    }
}
