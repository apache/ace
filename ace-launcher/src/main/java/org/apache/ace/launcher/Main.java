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
import org.osgi.framework.Constants;
import org.osgi.framework.launch.FrameworkFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple launcher, that launches the embedded Felix together with a management agent. It accepts the following
 * command line arguments,
 * <ul>
 * <li><tt>--fwOption=key=value</tt> framework options to be passed to the launched framework. This argument can be used
 * multiple times.</li>
 * </ul>
 * <br>
 * Furthermore, it inherits all system properties that the self-contained Management Agent accepts. 
 *
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (helpNecessary()) {
            showHelp();
            return;
        }

        FrameworkFactory factory = (FrameworkFactory) Class.forName("org.apache.felix.framework.FrameworkFactory").newInstance();

        List activators = new ArrayList();
        activators.add(new Activator());
        Map frameworkProperties = new HashMap();
        frameworkProperties.put("felix.systembundle.activators", activators);
        frameworkProperties.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.osgi.service.deploymentadmin;version=\"1.0\","
                + "org.osgi.service.deploymentadmin.spi;version=\"1.0\","
                + "org.osgi.service.cm;version=\"1.3\","
                + "org.osgi.service.event;version=\"1.2\","
                + "org.osgi.service.log;version=\"1.3\","
                + "org.osgi.service.metatype;version=\"1.1\","
                + "org.apache.ace.log;version=\"0.8.0\"");

        frameworkProperties.putAll(findFrameworkProperties(args));

        factory.newFramework(frameworkProperties).start();
    }

    private static boolean helpNecessary() {
        return (System.getProperty("identification") == null) || (System.getProperty("discovery") == null);
    }

    private static void showHelp() {
        System.out.println("Apache ACE Launcher\n"
                + "Usage:\n"
                + "  java -jar -Didentification=<id> -Ddiscovery=<ace-server> ace-launcher.jar <options>\n"
                + "  in which\n"
                + "    - <id> is the name of the target (targetID)\n"
                + "    - <ace-server> is a URL to the ACE server this target should connect to\n"
                + "    - <options> is a set of startup options\n"
                + "The options:\n"
                + "  fwOption: a framework option, to pass into the OSGi framework to be created. This option can be repeated."
                + "Example:\n"
                + "  java -jar -Didentification=MyTarget -Ddiscovery=http://provisioning.company.com:8080 ace-launcher.jar "
                + "fwOption=org.osgi.framework.system.packages.extra=sun.misc,com.sun.management");
    }

    static Map findFrameworkProperties(String[] args) {
        Pattern pattern = Pattern.compile("--(\\w*)=([.\\w]*)=(.*)");
        Map result = new HashMap();
        for (String arg : args) {
            Matcher m = pattern.matcher(arg);
            if (m.matches() && m.group(1).equals("fwOption")) {
                result.put(m.group(2), m.group(3));
            }
        }
        return result;
    }
}
