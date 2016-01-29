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
package org.apache.ace.client.repository.helper.bundle.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

/**
 * Activator class for the Bundle ArtifactHelper.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary<String, String> props = new Hashtable<>();
        props.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        BundleHelperImpl helperImpl = new BundleHelperImpl();
        manager.add(createComponent()
            .setInterface(ArtifactHelper.class.getName(), props)
            .setImplementation(helperImpl));
        manager.add(createComponent()
            .setInterface(ArtifactRecognizer.class.getName(), null)
            .setImplementation(helperImpl));
        manager.add(createComponent()
            .setInterface(BundleHelper.class.getName(), null)
            .setImplementation(helperImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do
    }
}