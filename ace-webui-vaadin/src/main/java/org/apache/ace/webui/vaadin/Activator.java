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
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.Button.ClickEvent;

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
        
        // show events
//        manager.add(createComponent()
//            .setImplementation(new EventHandler() {
//                public void handleEvent(Event event) {
//                    System.out.print("EVENT: " + event.getTopic());
//                    for (String key : event.getPropertyNames()) {
//                        System.out.print(" " + key + "=" + event.getProperty(key));
//                    }
//                    System.out.println();
//                }
//            })
//            .setInterface(EventHandler.class.getName(), new Properties() {{ put(EventConstants.EVENT_TOPIC, "*"); }} )
//        );
        
        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), null)
            .setImplementation(new UIExtensionFactory() {
                public Component create(Map<String, Object> context) {
                    final StatefulGatewayObject target = (StatefulGatewayObject) context.get("object");
                    Button button = new Button("i", new Button.ClickListener() {
                        public void buttonClick(ClickEvent event) {
                            event.getButton().getWindow().showNotification(
                                target.getID(), "Available version: " + target.getCurrentVersion() + ", installed version: " + target.getLastInstallVersion());
                        }
                    });
                    button.setStyleName("small");
                    return button;
                }
            })
        );
    }
    
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
