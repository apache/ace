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

package org.apache.ace.launcher;

import org.apache.ace.managementagent.Activator;
import org.osgi.framework.launch.FrameworkFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        FrameworkFactory factory = (FrameworkFactory) Class.forName("org.apache.felix.framework.FrameworkFactory").newInstance();

        List activators = new ArrayList();
        activators.add(new Activator());
        Map frameworkProperties = new HashMap();
        frameworkProperties.put("felix.systembundle.activators", activators);

        factory.newFramework(frameworkProperties).start();
    }
}
