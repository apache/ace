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
package org.apache.ace.webui.vaadin;

import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServlet;

import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(VaadinResourceHandler.class)
            .add(createServiceDependency()
                .setService(HttpService.class)
                .setRequired(true)
            )
        );
        // register the main application for the ACE UI client
        Properties props = new Properties();
        props.put(HttpConstants.ENDPOINT, "/ace");
        manager.add(createComponent()
            .setInterface(HttpServlet.class.getName(), props)
            .setImplementation(VaadinServlet.class)
        );
        
        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), new Properties() {{ put(UIExtensionFactory.EXTENSION_POINT_KEY, UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET); }})
            .setImplementation(new UIExtensionFactory() {
                public Component create(Map<String, Object> context) {
                    final NamedObject object = (NamedObject) context.get("object");
                    VerticalLayout vl = new VerticalLayout();
                    vl.setCaption("Info");
                    final NamedObject namedObject = (NamedObject) context.get("object");
                    final StatefulGatewayObject target = (StatefulGatewayObject) namedObject.getObject();
                    Label info = new Label("Target ID: " + namedObject.getName() + "<br />" +
                		"Installed version: " + target.getLastInstallVersion() + "<br />" +
        				"Available version: " + target.getCurrentVersion() + "<br />" +
						"Provisioning state: " + target.getProvisioningState() + "<br />" +
						"Registration state: " + target.getRegistrationState());
                    info.setContentMode(Label.CONTENT_XHTML);
                    vl.addComponent(info);
                    return vl;
                }
            })
        );
    }
    
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
