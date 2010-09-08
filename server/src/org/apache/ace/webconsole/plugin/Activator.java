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
package org.apache.ace.webconsole.plugin;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.Servlet;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

/**
 * @author Toni Menzel
 */
public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext bundleContext, DependencyManager manager) throws Exception {
        Dictionary dict = new Hashtable();
        dict.put( "felix.webconsole.label", WebUIConsoleServlet.LABEL );
        dict.put( "felix.webconsole.title", WebUIConsoleServlet.TITLE );
        manager.add(createComponent()
            .setInterface(Servlet.class.getName(), dict)
            .setImplementation(new WebUIConsoleServlet())
        );
    }

    @Override
    public void destroy(BundleContext bundleContext, DependencyManager manager) throws Exception {

    }
}
