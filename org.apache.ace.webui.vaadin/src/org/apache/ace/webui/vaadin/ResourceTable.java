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

import com.vaadin.ui.Button;
import com.vaadin.ui.Table;

/**
 * Provides a custom table for displaying OBR resources by their symbolic name and version.
 */
public class ResourceTable extends Table {

    public static final String PROPERTY_SYMBOLIC_NAME = "symbolic name";
    public static final String PROPERTY_VERSION = "version";
    public static final String PROPERTY_PURGE = "purge";

    public ResourceTable() {
        super("Artifacts");

        addContainerProperty(PROPERTY_SYMBOLIC_NAME, String.class, null);
        addContainerProperty(PROPERTY_VERSION, String.class, null);
        addContainerProperty(PROPERTY_PURGE, Button.class, null);

        setSizeFull();

        setSelectable(true);
        setMultiSelect(true);
        setImmediate(true);

        setHeight("15em");
    }
}
