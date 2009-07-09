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
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.BundleService;
import org.apache.ace.client.services.BundleServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Table class for the Bundles.
 */
public class BundleTable extends ObjectTable<BundleDescriptor> {
    private BundleServiceAsync m_bundleService = GWT.create(BundleService.class);

    BundleTable(StatusHandler handler) {
        super(handler, "Name");
    }

    @Override
    protected void callService(AsyncCallback<BundleDescriptor[]> callback) {
        m_bundleService.getBundles(callback);
    }

    @Override
    protected String getValue(BundleDescriptor gd, int column) {
        switch(column) {
        case 0: return gd.getName();
        }
        return null;
    }
}
