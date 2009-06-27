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
package org.apache.ace.ma;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

// TODO work in progress
public class Activator implements BundleActivator {
    private org.apache.ace.identification.property.Activator m_identification;
    private org.apache.ace.discovery.property.Activator m_discovery;
    private org.apache.ace.deployment.deploymentadmin.Activator m_deployment;
    private org.apache.ace.deployment.task.Activator m_task;
    private org.apache.ace.scheduler.Activator m_scheduler;
    private org.apache.ace.configurator.Activator m_configurator;
    private org.apache.ace.gateway.log.store.impl.Activator m_store;
    private org.apache.ace.gateway.log.Activator m_log;
    private org.apache.ace.log.listener.Activator m_logListener;

    public void start(BundleContext context) throws Exception {
        m_identification = new org.apache.ace.identification.property.Activator();
        m_discovery = new org.apache.ace.discovery.property.Activator();
        m_deployment = new org.apache.ace.deployment.deploymentadmin.Activator();
        m_task = new org.apache.ace.deployment.task.Activator();
        m_scheduler = new org.apache.ace.scheduler.Activator();
        m_configurator = new org.apache.ace.configurator.Activator();
        m_store = new org.apache.ace.gateway.log.store.impl.Activator();
        m_log = new org.apache.ace.gateway.log.Activator();
        m_logListener = new org.apache.ace.log.listener.Activator();

        m_identification.start(context);
        m_discovery.start(context);
        m_deployment.start(context);
        m_task.start(context);
        m_scheduler.start(context);
        m_configurator.start(context);
        m_store.start(context);
        m_log.start(context);
        m_logListener.start(context);
    }

    public void stop(BundleContext context) throws Exception {
        m_identification.stop(context);
        m_discovery.stop(context);
        m_deployment.stop(context);
        m_task.stop(context);
        m_scheduler.stop(context);
        m_configurator.stop(context);
        m_store.stop(context);
        m_log.stop(context);
        m_logListener.stop(context);
    }
}
