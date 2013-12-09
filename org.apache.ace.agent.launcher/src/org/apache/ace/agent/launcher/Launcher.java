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

package org.apache.ace.agent.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.LoggingHandler;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * A simple launcher, that launches the embedded OSGi framework together with a management agent. Additional bundles may be
 * installed by putting {@link BundleProvider} services on the classpath.
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("a", "agent", true, "agent id (default handler)");
        options.addOption("s", "serverurl", true, "server url (default handler)");
        options.addOption("v", "verbose", false, "verbose logging");
        options.addOption("c", "config", true, "configuration file (see below)");
        options.addOption("h", "help", false, "print this message");

        CommandLineParser parser = new BasicParser();
        CommandLine command = parser.parse(options, args);

        if (command.hasOption("h")) {
            printHelp(options);
            return;
        }

        Map<String, String> configuration = new Hashtable<String, String>();

        // first map all default properties
        Properties defaultProperties = loadDefaultProperties();
        for (Object key : defaultProperties.keySet()) {
            configuration.put((String) key, defaultProperties.getProperty((String) key));
        }

        // overwrite with user properties
        if (command.hasOption("c")) {
            Properties userProperties = loadUserProperties(command.getOptionValue("c"));
            if (userProperties != null) {
                for (Object key : userProperties.keySet()) {
                    configuration.put((String) key, userProperties.getProperty((String) key));
                }
            }
        }

        // convenience debug override
        if (command.hasOption("v")) {
            configuration.put("verbose", "true");
            configuration.put(AgentConstants.CONFIG_LOGGING_LEVEL, "DEBUG");
        }

        // set server urls
        if (command.hasOption("s")) {
            configuration.put("agent.discovery.serverurls", command.getOptionValue("s"));
        }

        // set agent id
        if (command.hasOption("a")) {
            configuration.put("agent.identification.agentid", command.getOptionValue("a"));
        }
        
        new Launcher(configuration).run();
    }

    private static void printHelp(Options options) {
        // if all else fails, this is our default jar name
        String jarName = "org.apache.ace.agent.launcher.felix.jar";
        // because we have to use an unofficial API to get to the command line
        // to find the name of the jar that was started
        String command = System.getProperty("sun.java.command");
        if (command != null) {
            String[] args = command.split(" ");
            if (args.length > 0) {
                jarName = args[0];
            }
        }
        HelpFormatter formatter = new HelpFormatter();
        formatter
            .printHelp(
                120,
                "java -jar " + jarName + " [options]",
                "\n\nOptions:\n\n", options,
                "\n\nConfiguration file format:\n\n" +
                "A configuration file that can contain framework or agent configuration settings. " +
                "If you specify a certain setting both on the command line and in a configuration file, the command line takes precedence. " +
                "Framework configuration should be prefixed with 'framework.' so for example 'framework.org.osgi.framework.bootdelegation' will become 'org.osgi.framework.bootdelegation'. " +
                "Agent configuration starts with 'agent.' and will not be replaced. " +
                "Available options are (not exclusive):\n" +
                "agent.identification.agentid  : A name to uniquely identify the target\n" +
                "agent.discovery.serverurls    : Location of the Apache ACE server\n" +
                "agent.controller.syncinterval : Synchronization interval in seconds\n" +
                "agent.controller.syncdelay    : Synchronization initial delay in seconds\n" +
                "\n\nAlso, you can create a folder called 'bundle' and put bundles in that folder that will be started by the launcher.\n"
                , false);
        }

    private static Properties loadDefaultProperties() throws IOException {
        Properties properties = new Properties();
        ClassLoader classloader = Launcher.class.getClassLoader();
        InputStream inStream = classloader.getResourceAsStream("org/apache/ace/agent/launcher/launcher-defaults.properties");
        try {

            properties.load(inStream);
            return properties;
        }
        finally {
            inStream.close();
        }
    }

    private static Properties loadUserProperties(String configFileArgument)
        throws IOException {
        File configFile = new File(configFileArgument);
        if (!configFile.exists() || !configFile.isFile()
            || !configFile.canRead()) {
            System.err.println("Can not access configuration file : " + configFileArgument);
            return null;
        }
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(configFile));
        }
        catch (IOException e) {
            System.err.println("Can not load configuration file : " + configFileArgument);
            return null;
        }
        return properties;
    }

    private final Map<String, String> m_configuration;
    private final boolean m_verbose;

    public Launcher(Map<String, String> configuration) {
        m_configuration = configuration;
        m_verbose = (m_configuration.get("verbose") != null) && Boolean.parseBoolean(m_configuration.get("verbose"));
    }

    /**
     * Main execution logic of the launcher; Start a framework, install bundles and pass configuration to the
     * {@link AgentFactory}.
     * 
     * @throws Exception on failure
     */
    public void run() throws Exception {

        try {
            FrameworkFactory frameworkFactory = loadFrameworkFactory();
            Map<String, String> frameworkProperties = createFrameworkProperties();
            if (m_verbose) {
                System.out.println("Launching OSGi framework\n factory\t: "
                    + frameworkFactory.getClass().getName()
                    + "\n properties\t: " + frameworkProperties);
            }

            Framework framework = frameworkFactory.newFramework(frameworkProperties);
            BundleContext context = null;
            framework.init();
            context = framework.getBundleContext();

            BundleProvider[] bundleProviders = loadBundleProviders();
            for (BundleProvider bundleProvider : bundleProviders) {
                installBundles(context, bundleProvider);
            }

            for (Entry<String, String> entry : m_configuration.entrySet()) {
                if (entry.getKey().startsWith(AgentConstants.CONFIG_KEY_NAMESPACE)) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }

            framework.start();

            if (m_verbose) {
                System.out.println("Startup complete..");
            }
            framework.waitForStop(0);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private Bundle[] installBundles(BundleContext context, BundleProvider extensionProvider) throws BundleException, IOException {
        List<Bundle> bundles = new ArrayList<Bundle>();
        for (String bundleName : extensionProvider.getBundleNames()) {
            if (m_verbose) {
                System.out.println("Installing bundle\t: " + bundleName);
            }
            InputStream inputStream = null;
            try {
                inputStream = extensionProvider.getInputStream(bundleName);
                bundles.add(context.installBundle(bundleName, inputStream));
            }
            finally {
                if (inputStream != null)
                    inputStream.close();
            }
        }
        for (Bundle bundle : bundles) {
            bundle.start();
        }
        return bundles.toArray(new Bundle[bundles.size()]);
    }

    /**
     * Load {@link FrameworkFactory} through the {@link ServiceLoader}.
     * 
     * @return the first factory
     * @throws Exception on failure
     */
    private FrameworkFactory loadFrameworkFactory() throws Exception {
        ServiceLoader<FrameworkFactory> frameworkFactoryLoader = ServiceLoader.load(FrameworkFactory.class);
        Iterator<FrameworkFactory> frameworkFactoryIterator = frameworkFactoryLoader.iterator();
        if (!frameworkFactoryIterator.hasNext()) {
            throw new IllegalStateException("Unable to load any FrameworkFactory");
        }
        return frameworkFactoryIterator.next();
    }

    /**
     * Load {@link BundleProvider}s through the {@link ServiceLoader}.
     * 
     * @return list of providers
     * @throws Exception on failure
     */
    private BundleProvider[] loadBundleProviders() throws Exception {
        ServiceLoader<BundleProvider> bundleFactoryLoader = ServiceLoader.load(BundleProvider.class);
        Iterator<BundleProvider> bundleFactoryIterator = bundleFactoryLoader.iterator();
        List<BundleProvider> bundelFactoryList = new ArrayList<BundleProvider>();
        while (bundleFactoryIterator.hasNext()) {
            bundelFactoryList.add(bundleFactoryIterator.next());
        }
        return bundelFactoryList.toArray(new BundleProvider[bundelFactoryList.size()]);
    }

    /**
     * Build the framework launch properties.
     * 
     * @return the launch properties
     * @throws Exception on failure
     */
    private Map<String, String> createFrameworkProperties() throws Exception {
        Map<String, String> frameworkProperties = new HashMap<String, String>();
        for (Entry<String, String> entry : m_configuration.entrySet()) {
            if (entry.getKey().startsWith("framework.")) {
                String frameworkKey = entry.getKey().replaceFirst("framework.", "");
                String frameworkValue = m_configuration.get(entry.getKey());
                frameworkProperties.put(frameworkKey, frameworkValue);
            }
        }
        return frameworkProperties;
    }
}
