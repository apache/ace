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
package org.apache.ace.useradmin.ui.osgi;

import java.util.Map;
import java.util.Properties;

import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.apache.ace.useradmin.ui.editor.impl.UserEditorImpl;
import org.apache.ace.useradmin.ui.vaadin.UserAdminButton;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.ui.Button;
import com.vaadin.ui.Component;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, final DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setInterface(UserEditor.class.getName(), null)
            .setImplementation(UserEditorImpl.class)
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true)
            )
        );
        Properties properties = new Properties();
        properties.put(UIExtensionFactory.EXTENSION_POINT_KEY, UIExtensionFactory.EXTENSION_POINT_VALUE_MENU);
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), properties)
            .setImplementation(new UIExtensionFactory() {
                    @Override
                    public Component create(Map<String, Object> context) {
                        Button b = new UserAdminButton((User) context.get("user"));
                        manager.add(createComponent()
                            .setImplementation(b)
                            .add(createServiceDependency()
                                .setService(UserEditor.class)
                                .setRequired(true)
                            )
                        );
                        b.setDescription("This button opens a window to manage users");
                        return b;
                    }
                }
            )
        );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
