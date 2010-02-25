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
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;
import org.apache.ace.client.services.TargetServiceAsync;

import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Table class for the targets.
 */
public class TargetTable extends ObjectTable<TargetDescriptor> {
    private TargetServiceAsync m_targetService = GWT.create(TargetService.class);
    private AssociationServiceAsync m_associationService = GWT.create(AssociationService.class);

    TargetTable(StatusHandler handler, PickupDragController dragController, Main main) {
        super(handler, dragController, main);
    }

    @Override
    protected void getDescriptors(AsyncCallback<TargetDescriptor[]> callback) {
        m_targetService.getTargets(callback);
    }

    @Override
    protected String getText(TargetDescriptor td) {
        return td.getName() + " - " + td.getProvisioningState().toString();
    }

    @Override
    protected String getTableID() {
        return "targets";
    }

    @Override
    protected void remove(TargetDescriptor object, AsyncCallback<Void> callback) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    protected boolean canDelete() {
        return false;
    }

    @Override
    protected void link(TargetDescriptor object, Descriptor other, AsyncCallback<Void> callback) {
        if (other instanceof LicenseDescriptor) {
            m_associationService.link((LicenseDescriptor) other, object, callback);
        }
    }
}
