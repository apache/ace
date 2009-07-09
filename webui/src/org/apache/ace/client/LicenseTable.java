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
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.LicenseService;
import org.apache.ace.client.services.LicenseServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Table class for the licenses.
 */
public class LicenseTable extends ObjectTable<LicenseDescriptor> {
    private LicenseServiceAsync m_licenseService = GWT.create(LicenseService.class);

    LicenseTable(StatusHandler handler) {
        super(handler, "Name");
    }

    @Override
    protected void callService(AsyncCallback<LicenseDescriptor[]> callback) {
        m_licenseService.getLicenses(callback);
    }

    @Override
    protected String getValue(LicenseDescriptor ld, int column) {
        switch(column) {
        case 0: return ld.getName();
        }
        return null;
    }

    void addNew() {
        String result = Window.prompt("Add license", "New license");
        if (result != null) {
            m_licenseService.addLicense(result, new AsyncCallback<Void>() {
                public void onFailure(Throwable caught) {
                    Window.alert("Error adding license.");
                }

                public void onSuccess(Void result) {
                    // Hurrah!
                }
                
            });
        }
    }
}
