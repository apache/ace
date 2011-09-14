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
 * A simple launcher, that launches the embedded Felix together with a management agent.
 */
public class Main {

    private Argument m_identification = new KeyValueArgument() {
        public void handle(String key, String value) {
            if (key.equals("identification")) {
                System.setProperty("identification", value);
            }
        }

        public String getDescription() {
            return "identification: sets the target ID to use";
        }
    };

    private Argument m_discovery = new KeyValueArgument() {
        public void handle(String key, String value) {
            if (key.equals("discovery")) {
                System.setProperty("discovery", value);
            }
        }

        public String getDescription() {
            return "discovery: sets the ACE server to connect to";
        }
    };

    private Argument m_agents = new KeyValueArgument() {
        public void handle(String key, String value) {
            if (key.equals("agents")) {
                System.setProperty("agents", value);
            }
        }

        public String getDescription() {
            return "agents: configures multiple management agents: agent-id,identification,discovery[;agent-id,identification,discovery]*";
        }
    };

    private Argument m_help = new Argument() {
        public void handle(String argument) {
            if (argument.equals("help")) {
                showHelp();
                System.exit(0);
            }
        }

        public String getDescription() {
            return "help: prints this help message";
        }
    };

    private FrameworkOption m_fwOptionHandler = new FrameworkOption();

    private final List<Argument> m_arguments = Arrays.asList(
        m_identification,
        m_discovery,
        m_agents,
        m_fwOptionHandler,
        m_help);

    public static void main(String[] args) throws Exception {
        new Main(args).run();
    }

    public Main(String[] args) {
        for (String arg : args) {
            for (Argument argument : m_arguments) {
                argument.handle(arg);
            }
        }
    }

    public void run() throws Exception {
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

        frameworkProperties.putAll(m_fwOptionHandler.getProperties());

        factory.newFramework(frameworkProperties).start();
    }

    private void showHelp() {
        System.out.println("Apache ACE Launcher\n"
                + "Usage:\n"
                + "  java -jar ace-launcher.jar [identification=<id>] [discovery=<ace-server>] [options...]");

        System.out.println("All known options are,");
        for (Argument argument : m_arguments) {
            System.out.println("  " + argument.getDescription());
        }

        System.out.println("Example:\n"
                + "  java -jar ace-launcher.jar identification=MyTarget discovery=http://provisioning.company.com:8080 "
                + "fwOption=org.osgi.framework.system.packages.extra=sun.misc,com.sun.management");
    }

    private interface Argument {
        void handle(String argument);
        String getDescription();
    }

    private static abstract class KeyValueArgument implements Argument {
        public void handle(String argument) {
            Pattern pattern = Pattern.compile("(\\w*)=(.*)");
            Matcher m = pattern.matcher(argument);
            if (m.matches()) {
                handle(m.group(1), m.group(2));
            }
        }

        protected abstract void handle(String key, String value);
    }

    private static class FrameworkOption extends KeyValueArgument {
        private Properties m_properties = new Properties();
        @Override
        protected void handle(String key, String value) {
            if (key.equals("fwOption")) {
                Pattern pattern = Pattern.compile("([^=]*)=(.*)");
                Matcher m = pattern.matcher(value);
                if (!m.matches()) {
                    throw new IllegalArgumentException(value + " is not a valid framework option.");
                }
                m_properties.put(m.group(1), m.group(2));
            }
        }

        public String getDescription() {
            return "fwOption: sets framework options for the OSGi framework to be created. This argument may be repeated";
        }

        public Properties getProperties() {
            return m_properties;
        }
    }
}
