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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * A simple launcher, that launches the embedded Felix together with a management agent.
 */
public class Main {

    private static class AdditionalBundlesOption extends KeyValueArgument {
        private final List<String> m_additionalBundleActivators;

        public AdditionalBundlesOption() {
            super("bundle");

            m_additionalBundleActivators = new ArrayList<String>();
        }

        /**
         * @return the additionalBundleActivators
         */
        public List<String> getAdditionalBundleActivators() {
            return new ArrayList<String>(m_additionalBundleActivators);
        }

        @Override
        public String getDescription() {
            return "bundle: adds an additional bundle to be started with this management agent: bundle=my.fully.qualified.BundleActivator";
        }

        @Override
        protected void doHandle(String value) {
            if (!"".equals(value.trim()) && !m_additionalBundleActivators.contains(value)) {
                m_additionalBundleActivators.add(value);
            }
        }
    }

    private interface Argument {
        String getDescription();

        void handle(String argument);
    }

    private static class FrameworkOption extends KeyValueArgument {
        private Properties m_properties = new Properties();

        public FrameworkOption() {
            super("fwOption");
        }

        public String getDescription() {
            return "fwOption: sets framework options for the OSGi framework to be created. This argument may be repeated";
        }

        public Properties getProperties() {
            return m_properties;
        }

        @Override
        protected void doHandle(String value) {
            Pattern pattern = Pattern.compile("([^=]*)=(.*)");
            Matcher m = pattern.matcher(value);
            if (!m.matches()) {
                throw new IllegalArgumentException(value + " is not a valid framework option.");
            }
            m_properties.put(m.group(1), m.group(2));
        }
    }

    private static abstract class KeyValueArgument implements Argument {
        protected final String m_key;

        public KeyValueArgument(String key) {
            m_key = key;
        }

        public void handle(String argument) {
            Pattern pattern = Pattern.compile(m_key + "=(.*)");
            Matcher m = pattern.matcher(argument);
            if (m.matches()) {
                doHandle(m.group(1));
            }
        }

        protected abstract void doHandle(String value);
    }

    private static class SystemPropertyArgument extends KeyValueArgument {
        private final String m_description;

        public SystemPropertyArgument(String key, String description) {
            super(key);
            m_description = description;
        }

        @Override
        public String getDescription() {
            return m_key + ": " + m_description;
        }

        @Override
        protected void doHandle(String value) {
            System.setProperty(m_key, value);
        }
    }

    private static final boolean m_quiet = Boolean.parseBoolean(System.getProperty("quiet", "false"));

    /**
     * MAIN ENTRY POINT
     * 
     * @param args
     *            the command line arguments, never <code>null</code>.
     * @throws Exception
     *             in case of errors.
     */
    public static void main(String[] args) throws Exception {
        new Main(args).run();
    }

    private final FrameworkOption m_fwOptionHandler;
    private final AdditionalBundlesOption m_additionalBundleHandler;

    /**
     * Creates a new {@link Main} instance.
     * 
     * @param args
     *            the command line arguments, never <code>null</code>.
     */
    public Main(String[] args) {
        m_additionalBundleHandler = new AdditionalBundlesOption();
        m_fwOptionHandler = new FrameworkOption();

        final List<Argument> arguments = new ArrayList<Argument>();

        Argument agents = new SystemPropertyArgument("agents", "configures multiple management agents: agent-id,identification,discovery[;agent-id,identification,discovery]*");
        Argument auth = new SystemPropertyArgument("auth", "point to the properties file containing the authentication credentials for a certain subsystem: <dir/file/url>");
        Argument discovery = new SystemPropertyArgument("discovery", "sets the ACE server to connect to");
        Argument identification = new SystemPropertyArgument("id(?:entification)?", "sets the target ID to use") {
            @Override
            protected void doHandle(String value) {
                System.setProperty("identification", value);
            }
        };
        Argument help = new Argument() {
            public String getDescription() {
                return "help: prints this help message";
            }

            public void handle(String argument) {
                if ("help".equals(argument)) {
                    showHelp(arguments);
                    System.exit(0);
                }
            }
        };

        arguments.addAll(Arrays.asList(agents, auth, discovery, identification, m_additionalBundleHandler, m_fwOptionHandler, help));

        for (String arg : args) {
            for (Argument argument : arguments) {
                argument.handle(arg);
            }
        }
    }

    public void run() throws Exception {
        Map frameworkProperties = createFrameworkProperties();
        FrameworkFactory factory = createFrameworkFactory();

        Framework framework = factory.newFramework(frameworkProperties);

        framework.start();

        try {
            framework.waitForStop(0);
        }
        finally {
            System.exit(0);
        }
    }

    /**
     * @param extraSystemPackages
     * @return
     */
    private String getExtraSystemPackages(String[] extraSystemPackages) {
        String isolateMA = System.getProperty("isolateMA", "managementagent");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < extraSystemPackages.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(extraSystemPackages[i]);
            if (isolateMA != null && !"".equals(isolateMA.trim())) {
                sb.append(String.format(";%1$s=true;mandatory:=%1$s", isolateMA));
            }
        }
        return sb.toString();
    }

    /**
     * @return
     */
    private FrameworkFactory createFrameworkFactory() {
        try {
            Class<?> clazz = Class.forName("org.apache.felix.framework.FrameworkFactory");
            return (FrameworkFactory) clazz.newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create framework factory?!", e);
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private Map createFrameworkProperties() throws Exception {
        String[] extraSystemPackageArray = {
            "org.osgi.service.deploymentadmin;version=\"1.0\"",
            "org.osgi.service.deploymentadmin.spi;version=\"1.0\"",
            "org.osgi.service.cm;version=\"1.3\"",
            "org.osgi.service.event;version=\"1.2\"",
            "org.osgi.service.log;version=\"1.3\"",
            "org.osgi.service.metatype;version=\"1.1\"",
            "org.apache.felix.dm;version=\"3.0\"",
            "org.apache.felix.dm.tracker;version=\"3.0\"",
            "org.apache.ace.log;version=\"0.8\"",
            "org.apache.ace.deployment.service;version=\"0.8\""
        };

        Map frameworkProperties = new HashMap();
        frameworkProperties.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP, getSystemBundleActivators());
        frameworkProperties.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, getExtraSystemPackages(extraSystemPackageArray));
        frameworkProperties.putAll(m_fwOptionHandler.getProperties());

        return frameworkProperties;
    }

    /**
     * @return
     * @throws IOException
     */
    private List<String> getAdditionalBundleActivators() throws IOException {
        List<String> bundleActivators = m_additionalBundleHandler.getAdditionalBundleActivators();

        // The actual management agent itself...
        bundleActivators.add(0, "org.apache.ace.managementagent.Activator");

        // Pull in all the additional mentioned bundles on the classpath...
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources("META-INF/MANIFEST.MF");
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try {
                Manifest mf = new Manifest(resource.openStream());
                String bundleActivator = mf.getMainAttributes().getValue(Constants.BUNDLE_ACTIVATOR);
                if (bundleActivator != null) {
                    bundleActivators.add(bundleActivator);
                }
            }
            catch (Exception e) {
                System.err.println("Failed to read resource: " + resource + "!\nPossible reason: " + e);
            }
        }
        return bundleActivators;
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Object> getSystemBundleActivators() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        List<Object> result = new ArrayList<Object>();

        List<String> bundleActivators = getAdditionalBundleActivators();
        for (String bundleActivator : bundleActivators) {
            try {
                Object instance = cl.loadClass(bundleActivator).newInstance();
                if (!m_quiet) {
                    System.out.println("Adding additional bundle activator: " + bundleActivator);
                }
                result.add(instance);
            }
            catch (Exception e) {
                System.err.println("Failed to add bundle activator: " + bundleActivator + "!\nPossible reason: " + e);
            }
        }

        return result;
    }

    private void showHelp(List<Argument> arguments) {
        System.out.println("Apache ACE Launcher\n"
            + "Usage:\n"
            + "  java -jar ace-launcher.jar [identification=<id>] [discovery=<ace-server>] [options...]");

        System.out.println("All known options are:");
        for (Argument argument : arguments) {
            System.out.println("  " + argument.getDescription());
        }

        System.out.println("Example:\n"
            + "  java -jar ace-launcher.jar identification=MyTarget discovery=http://provisioning.company.com:8080 "
            + "fwOption=org.osgi.framework.system.packages.extra=sun.misc,com.sun.management");
    }
}
