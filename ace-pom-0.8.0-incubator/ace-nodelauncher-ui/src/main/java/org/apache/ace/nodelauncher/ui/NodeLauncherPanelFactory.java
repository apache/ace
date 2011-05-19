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
package org.apache.ace.nodelauncher.ui;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.nodelauncher.NodeLauncher;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.osgi.service.log.LogService;

import com.vaadin.ui.Panel;

public class NodeLauncherPanelFactory implements UIExtensionFactory {
    private volatile NodeLauncher m_nodeLauncher;
    private volatile LogService m_log;
    
    private final ExecutorService m_executor = Executors.newCachedThreadPool();
    
    public Panel create(Map<String, Object> context) {
        NamedObject namedObject = (NamedObject) context.get("object");
        StatefulGatewayObject target = (StatefulGatewayObject) namedObject.getObject();
        return new NodePanel(this, target.getID());
    }
    
    NodeLauncher getCloudService() {
        return m_nodeLauncher;
    }
    
    LogService getLogService() {
        return m_log;
    }
    
    Future<?> submit(Runnable runnable) {
        return m_executor.submit(runnable);
    }
}
