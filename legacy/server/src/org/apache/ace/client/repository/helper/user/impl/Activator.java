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
package org.apache.ace.client.repository.helper.user.impl;

import java.util.Properties;

import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.user.UserAdminHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

/**
 * Activator class for the UserAdmin ArtifactHelper.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(ArtifactObject.KEY_MIMETYPE, UserAdminHelper.MIMETYPE);
        UserHelperImpl helperImpl = new UserHelperImpl();
        manager.add(createComponent()
            .setInterface(ArtifactHelper.class.getName(), props)
            .setImplementation(helperImpl));
        props = new Properties();
        props.put(Constants.SERVICE_RANKING, 10);
        manager.add(createComponent()
            .setInterface(ArtifactRecognizer.class.getName(), props)
            .setImplementation(helperImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do
    }
}
