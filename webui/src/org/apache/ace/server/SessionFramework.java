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
package org.apache.ace.server;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.osgi.framework.Bundle;

/**
 * Provides access to a client session specific framework
 * instance.
 */
public class SessionFramework {
    public static Bundle getFramework(HttpSession session, File baseDir) throws Exception {
        Bundle fw = (Bundle) session.getAttribute(SessionConstants.FRAMEWORK);
        if (fw == null) {
            try {
                Map configMap = new HashMap();
                configMap.put("org.osgi.framework.system.packages.extra", "my.package.one, my.package.two");
                configMap.put("org.osgi.framework.storage.clean", "true");
                configMap.put("felix.cache.rootdir", new File(baseDir, "caches"));
                String cacheDirName = "cache_" + session.getId();
                configMap.put("org.osgi.framework.storage", cacheDirName);
    
                String systemBundlesList = 
                    "file:bundle/org.apache.felix.shell-1.2.0.jar " + 
                    "file:bundle/org.apache.felix.shell.tui-1.2.0.jar";
                configMap.put("felix.auto.start.1", systemBundlesList);

                List bundles = new ArrayList();
                
                Class autoActivatorClass = Class.forName("org.apache.felix.main.AutoActivator");
                Constructor autoActivatorConstructor = autoActivatorClass.getConstructor(new Class[] { Map.class });
                Object autoActivator = autoActivatorConstructor.newInstance(new Object[] { configMap });
                
                bundles.add(autoActivator);
                
                configMap.put("felix.systembundle.activators", bundles);
    
                Class felixClass = Class.forName("org.apache.felix.framework.Felix");
                Constructor felixConstructor = felixClass.getConstructor(new Class[] { Map.class });
                Bundle felix = (Bundle) felixConstructor.newInstance(new Object[] { configMap });
                felix.start();
                session.setAttribute(SessionConstants.FRAMEWORK, felix);
                session.setAttribute(SessionConstants.CACHEDIR, cacheDirName);
                return felix;
            }
            catch (Exception error) {
                throw error;
            }
        }
        return fw;
    }
}
