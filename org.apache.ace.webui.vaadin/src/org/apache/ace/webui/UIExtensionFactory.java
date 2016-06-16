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
package org.apache.ace.webui;

import java.util.Map;

import org.osgi.annotation.versioning.ConsumerType;

import com.vaadin.ui.Component;

/**
 * Creates components for named extension points in the Vaadin UI. Extension factories
 * are used throughout the UI to allow other bundles to contribute features.
 */
@ConsumerType
public interface UIExtensionFactory {
    public static final String EXTENSION_POINT_KEY = "extension_point";
    public static final String EXTENSION_POINT_VALUE_ARTIFACT = "artifact";
    public static final String EXTENSION_POINT_VALUE_FEATURE = "feature";
    public static final String EXTENSION_POINT_VALUE_DISTRIBUTION = "distribution";
    public static final String EXTENSION_POINT_VALUE_TARGET = "target";
    public static final String EXTENSION_POINT_VALUE_MENU = "menu";
    
    /**
     * Creates a UI component for use in the extension point. The contents of the
     * context are extension-point dependent.
     */
    Component create(Map<String, Object> context);
}
