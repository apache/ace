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

import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_KEY;
import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_VALUE_MENU;
import static org.osgi.framework.Constants.SERVICE_RANKING;

import java.util.Map;
import java.util.Properties;

import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.apache.ace.useradmin.ui.editor.impl.UserEditorImpl;
import org.apache.ace.useradmin.ui.vaadin.EditUserInfoButton;
import org.apache.ace.useradmin.ui.vaadin.UserAdminButton;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.ui.Component;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, final DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setInterface(UserEditor.class.getName(), null)
            .setImplementation(UserEditorImpl.class)
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true)));

        Properties properties = new Properties();
        properties.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_MENU);
        properties.put(SERVICE_RANKING, 102);

        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), properties)
            .setImplementation(new UIExtensionFactory() {
                @Override
                public Component create(Map<String, Object> context) {
                    EditUserInfoButton b = new EditUserInfoButton();
                    manager.add(createComponent()
                        .setImplementation(b)
                        .setComposition("getComposition")
                        .add(createServiceDependency()
                            .setService(UserEditor.class)
                            .setRequired(true))
                        .add(createServiceDependency()
                            .setService(LogService.class)
                            .setRequired(false)));
                    return b;
                }
            }));

        properties = new Properties();
        properties.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_MENU);
        properties.put(SERVICE_RANKING, 101);

        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), properties)
            .setImplementation(new UIExtensionFactory() {
                @Override
                public Component create(Map<String, Object> context) {
                    UserAdminButton b = new UserAdminButton();
                    manager.add(createComponent()
                        .setImplementation(b)
                        .setComposition("getComposition")
                        .add(createServiceDependency()
                            .setService(UserEditor.class)
                            .setRequired(true))
                        .add(createServiceDependency()
                            .setService(LogService.class)
                            .setRequired(false)));
                    return b;
                }
            }));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
