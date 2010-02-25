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
import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.AssociationServiceAsync;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.LicenseService;
import org.apache.ace.client.services.LicenseServiceAsync;
import org.apache.ace.client.services.TargetDescriptor;

import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Table class for the licenses.
 */
public class LicenseTable extends ObjectTable<LicenseDescriptor> {
    private LicenseServiceAsync m_licenseService = GWT.create(LicenseService.class);
    private AssociationServiceAsync m_associationService = GWT.create(AssociationService.class);

    LicenseTable(StatusHandler handler, PickupDragController dragController, Main main) {
        super(handler, dragController, main);
    }

    @Override
    protected void getDescriptors(AsyncCallback<LicenseDescriptor[]> callback) {
        m_licenseService.getLicenses(callback);
    }

    @Override
    protected String getText(LicenseDescriptor ld) {
        return ld.getName();
    }

    void addNew() {
        String result = Window.prompt("Add distribution", "New distribution");
        if (result != null) {
            m_licenseService.addLicense(result, new AsyncCallback<Void>() {
                public void onFailure(Throwable caught) {
                    Window.alert("Error adding distribution.");
                }
                public void onSuccess(Void result) {
                    // Hurrah!
                }
            });
        }
    }

    @Override
    protected String getTableID() {
        return "licenses";
    }

    @Override
    protected void remove(LicenseDescriptor object, AsyncCallback<Void> callback) {
        m_licenseService.remove(object, callback);
    }

    @Override
    protected void link(LicenseDescriptor object, Descriptor other, AsyncCallback<Void> callback) {
        if (other instanceof GroupDescriptor) {
            m_associationService.link((GroupDescriptor) other, object, callback);
        }
        else if (other instanceof TargetDescriptor) {
            m_associationService.link(object, (TargetDescriptor) other, callback);
        }
    }
}