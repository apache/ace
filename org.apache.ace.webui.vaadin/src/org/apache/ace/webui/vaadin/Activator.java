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

import javax.servlet.Servlet;

import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.http.HttpService;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

public class Activator extends DependencyActivatorBase {
    private static final String PID = "org.apache.ace.webui.vaadin";

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

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
        manager.add(createComponent()
            .setInterface(Servlet.class.getName(), null)
            .setImplementation(VaadinServlet.class)
            .add(createConfigurationDependency()
                .setPid(PID).setPropagate(true))
            );

        Properties props = new Properties();
        props.put(UIExtensionFactory.EXTENSION_POINT_KEY, UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET);
        props.put(Constants.SERVICE_RANKING, Integer.valueOf(100));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(new UIExtensionFactory() {
                public Component create(Map<String, Object> context) {
                    VerticalLayout vl = new VerticalLayout();
                    vl.setCaption("Info");
                    final StatefulTargetObject target = (StatefulTargetObject) context.get("statefulTarget");
                    Label info = new Label("Target ID          : " + target.getID() + "\n" +
                        "Installed version  : " + (target.getLastInstallVersion() == null ? "(none)" : target.getLastInstallVersion()) + "\n" +
                        "Available version  : " + target.getCurrentVersion() + "\n" +
                        "Approval state     : " + target.getApprovalState() + "\n" +
                        "Store state        : " + target.getStoreState() + "\n" +
                        "Provisioning state : " + target.getProvisioningState() + "\n" +
                        "Registration state : " + target.getRegistrationState());
                    info.setContentMode(Label.CONTENT_PREFORMATTED);
                    vl.addComponent(info);
                    return vl;
                }
            })
            );
    }
}
