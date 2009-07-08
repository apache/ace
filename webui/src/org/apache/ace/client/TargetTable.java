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
package org.apache.ace.client;

import org.apache.ace.client.Main.StatusHandler;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;
import org.apache.ace.client.services.TargetServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * Table class for the targets.
 */
public class TargetTable extends FlexTable {
    private TargetServiceAsync m_targetService = GWT.create(TargetService.class);
    private final StatusHandler m_handler;

    TargetTable(StatusHandler handler) {
        m_handler = handler;
        setText(0, 0, "Name");
        setText(0, 1, "Status");
    }
    
    void updateTable() {
        m_targetService.getTargets(new AsyncCallback<TargetDescriptor[]>() {
            public void onFailure(Throwable caught) {
                m_handler.handleFail(getClass());
            }
            public void onSuccess(TargetDescriptor[] result) {
                m_handler.handleSuccess(getClass());
                int row = 1;
                for (TargetDescriptor td : result) {
                    setText(row, 0, td.getName());
                    setText(row, 1, td.getProvisioningState().toString());
                    row++;
                }
            }
        });
    }
}
