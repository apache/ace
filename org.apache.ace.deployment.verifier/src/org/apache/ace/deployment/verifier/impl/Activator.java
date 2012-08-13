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
package org.apache.ace.deployment.verifier.impl;

import org.apache.ace.deployment.verifier.VerifierService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Provides a {@link BundleActivator} implementation for the {@link VerifierService}.
 */
public class Activator implements BundleActivator {

    /**
     * {@inheritDoc}
     */
    public void start(BundleContext context) {
        context.registerService(VerifierService.class.getName(), new VerifierServiceImpl(), null);
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext context) {
        // Nop
    }
}
