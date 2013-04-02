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

package org.apache.ace.authentication.processor.basicauth;

import java.util.Properties;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;

/**
 * Provides a bundle activator for the {@link BasicHttpAuthenticationProcessor}.
 */
public class Activator extends DependencyActivatorBase {

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, BasicHttpAuthenticationProcessor.PID);
        
// @formatter:off
        manager.add(createComponent()
            .setInterface(new String[]{ AuthenticationProcessor.class.getName(), ManagedService.class.getName() }, props)
            .setImplementation(new BasicHttpAuthenticationProcessor())
        );
// @formatter:on
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nop
    }
}
