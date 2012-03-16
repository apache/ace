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
package org.apache.ace.target.management.ui;

import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;

/**
 * Provides a simple management UI for targets.
 */
public class TargetManagementExtension implements UIExtensionFactory {

    private static final String CAPTION = "Management";

    /**
     * {@inheritDoc}
     */
    public Component create(Map<String, Object> context) {
        GridLayout result = new GridLayout(1, 4);
        result.setCaption(CAPTION);

        result.setMargin(true);
        result.setSpacing(true);
        result.setSizeFull();

        RepositoryObject object = getRepositoryObjectFromContext(context);
        if (!(object instanceof StatefulTargetObject)) {
            result.addComponent(new Label("This target is not a stateful gateway object."));
            return result;
        }

        final StatefulTargetObject target = (StatefulTargetObject) object;

        CheckBox registerCB = new CheckBox("Registered?");
        registerCB.setImmediate(true);
        registerCB.setEnabled(!target.isRegistered());
        registerCB.setValue(Boolean.valueOf(target.isRegistered()));
        registerCB.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                if (event.getButton().booleanValue()) {
                    target.register();
                }
            }
        });

        result.addComponent(registerCB);

        CheckBox autoApproveCB = new CheckBox("Auto approve?");
        autoApproveCB.setImmediate(true);
        autoApproveCB.setEnabled(target.isRegistered());
        autoApproveCB.setValue(Boolean.valueOf(target.getAutoApprove()));
        autoApproveCB.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                target.setAutoApprove(event.getButton().booleanValue());
            }
        });

        result.addComponent(autoApproveCB);


        Button approveButton = new Button("Approve changes");
        approveButton.setImmediate(true);
        approveButton.setEnabled(!target.getAutoApprove() && target.isRegistered() && hasUnapprovedChanges(target));
        approveButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                target.approve();
            }
        });

        result.addComponent(approveButton);
        
        // Add a spacer that fill the remainder of the available space...
        result.addComponent(new Label(" "));
        result.setRowExpandRatio(3, 1.0f);

        return result;
    }

    /**
     * @param target
     * @return
     */
    private boolean hasUnapprovedChanges(StatefulTargetObject target) {
        String availableVersion = target.getCurrentVersion();
        String currentVersion = target.getLastInstallVersion();
        return (availableVersion != null) && !availableVersion.equals(currentVersion);
    }

    private RepositoryObject getRepositoryObjectFromContext(Map<String, Object> context) {
        Object contextObject = context.get("object");
        if (contextObject == null) {
            throw new IllegalStateException("No context object found");
        }
        // It looks like there is some bug (or some other reason that escapes
        // me) why ace is using either the object directly or wraps it in a
        // NamedObject first.
        // Its unclear when it does which so for now we cater for both.
        return (contextObject instanceof NamedObject ? ((NamedObject) contextObject).getObject()
            : (RepositoryObject) contextObject);
    }
}
