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
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.GroupService;
import org.apache.ace.client.services.GroupServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Table class for the groups.
 */
public class GroupTable extends ObjectTable<GroupDescriptor> {
    private GroupServiceAsync m_groupService = GWT.create(GroupService.class);

    GroupTable(StatusHandler handler) {
        super(handler);
    }

    @Override
    protected void callService(AsyncCallback<GroupDescriptor[]> callback) {
        m_groupService.getGroups(callback);
    }

    @Override
    protected String getText(GroupDescriptor gd) {
        return gd.getName();
    }
    
    void addNew() {
        String result = Window.prompt("Add group", "New group");
        if (result != null) {
            m_groupService.addGroup(result, new AsyncCallback<Void>() {
                public void onFailure(Throwable caught) {
                    Window.alert("Error adding group.");
                }

                public void onSuccess(Void result) {
                    // Hurrah!
                }
                
            });
        }
    }

    @Override
    protected String getTableID() {
        return "groups";
    }


}
