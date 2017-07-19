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
package org.apache.ace.webui.vaadin.extension;

import org.apache.ace.client.repository.stateful.StatefulTargetObject;

import com.vaadin.ui.Table;

/**
 * Provides a UI extension for the target details window showing some details on a (stateful) target.
 */
public class TargetInfoExtensionFactory extends BaseInfoExtensionFactory<StatefulTargetObject> {

    @Override
    protected void addTableRows(Table table, StatefulTargetObject target) {
        addItem(table, "Target ID", target.getID());
        addItem(table, "Available version", target.getCurrentVersion());
        addItem(table, "Installed version", (target.getLastInstallVersion() == null ? "(none)" : target.getLastInstallVersion()));
        addItem(table, "Registration state", target.getRegistrationState());
        addItem(table, "Approval state", target.getApprovalState());
        addItem(table, "Provisioning state", target.getProvisioningState());
        addItem(table, "Store state", target.getStoreState());
    }
}
