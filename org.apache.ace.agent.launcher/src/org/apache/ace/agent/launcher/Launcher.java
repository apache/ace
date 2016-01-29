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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.ace.agent.AgentConstants;
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

import static org.apache.commons.cli.OptionBuilder.*;

/**
 * A simple launcher, that launches the embedded OSGi framework together with a management agent. Additional bundles may
 * be installed by putting {@link BundleProvider} services on the classpath.
 */
public class Launcher implements PropertyProvider {
    private static final String SYSTEM_PREFIX = "system.";
    private static final String FRAMEWORK_PREFIX = "framework.";

    /**
     * MAIN ENTRY POINT
     */
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.parseArgs(args);
        launcher.run();
    }

    private Map<String, String> m_configuration;

    /**
     * @return the value of the property with the given key.
     */
    public String getProperty(String key) {
        String value = m_configuration.get(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value;
    }

    public Map<String, String> parseArgs(String... args) throws Exception {
        Options options = new Options();
        addOption(options, 'a', "agent", "ID", "the agent ID to use");
        addOption(options, 's', "serverurl", "URLs", "the Apache ACE server URL(s) to use");
        addOption(options, 'v', "verbose", "enable verbose logging");
        addOption(options, 'c', "config", "FILE", "use configuration file");
        addOption(options, 'h', "help", "prints this message");

        // Start from scratch...
        Map<String, String> config = new HashMap<>();

        CommandLineParser parser = new BasicParser();
        CommandLine command = parser.parse(options, args, false /* stopAtNonOption */);

        if (command.hasOption("h")) {
            printHelp(options);
            System.exit(0);
        }

        // first map all default properties
        propagateConfiguration(loadDefaultProperties(), config);

        // overwrite with user properties
        if (command.hasOption("c")) {
            propagateConfiguration(loadUserProperties(command.getOptionValue("c")), config);
        }

        // add all non-recognized command line options...
        propagateConfiguration(command.getArgs(), config);

        // convenience debug override...
        if (command.hasOption("v")) {
            config.put("verbose", "true");
            config.put(AgentConstants.CONFIG_LOGGING_LEVEL, "DEBUG");
        }

        // handle overrides...
        if (command.hasOption("s")) {
            config.put(AgentConstants.CONFIG_DISCOVERY_SERVERURLS, command.getOptionValue("s"));
        }
        if (command.hasOption("a")) {
            config.put(AgentConstants.CONFIG_IDENTIFICATION_AGENTID, command.getOptionValue("a"));
        }

        return (m_configuration = config);
    }

    private void addOption(Options options, char opt, String longopt, String argName, String description) {
        withDescription(description);
        hasArg();
        withArgName(argName);
        withLongOpt(longopt);
        options.addOption(create(opt));
    }

    private void addOption(Options options, char opt, String longopt, String description) {
        hasArg(false);
        withDescription(description);
        withLongOpt(longopt);
        options.addOption(create(opt));
    }

    /**
     * Main execution logic of the launcher; Start a framework and install bundles.
     * <p>
     * This method never returns. It waits for the Framework to stop, and upon completion it will {@code System.exit()}.
     * </p>
     */
    public void run() {
        try {
            FrameworkFactory frameworkFactory = loadFrameworkFactory();
            BundleProvider[] bundleProviders = loadBundleProviders();

            logVerbose("Launching OSGi framework\n- factory:\t%s\n- properties:\t%s\n- providers:\t%s\n",
                frameworkFactory.getClass().getName(), m_configuration, Arrays.toString(bundleProviders));

            final Framework framework = frameworkFactory.newFramework(m_configuration);
            installShutdownHook(framework);
            framework.init();

            BundleContext context = framework.getBundleContext();

            for (BundleProvider bundleProvider : bundleProviders) {
                installBundles(context, bundleProvider);
            }

            framework.start();

            logVerbose("Startup complete...");

            framework.waitForStop(0);
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void installShutdownHook(final Framework framework) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    framework.stop();
                    framework.waitForStop(0);
                }
                catch (BundleException | InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }
        }, "Apache ACE Shutdown Hook"));
    }

    /**
     * @return the configuration
     */
    final Map<String, String> getConfiguration() {
        return m_configuration;
    }

    private void close(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (Exception exception) {
                // Ignore, nothing we can do about this...
            }
        }
    }

    private Bundle[] installBundles(BundleContext context, BundleProvider bundleProvider) throws BundleException, IOException {
        URL[] bundles = bundleProvider.getBundles(this);
        List<Bundle> result = new ArrayList<>(bundles.length);
        for (URL bundle : bundles) {
            logVerbose("- installing:\t%s%n", bundle.getFile());

            InputStream is = null;
            try {
                is = bundle.openStream();
                result.add(context.installBundle(bundle.toExternalForm(), is));
            }
            finally {
                close(is);
            }
        }
        for (Bundle bundle : result) {
            bundle.start();
        }
        return result.toArray(new Bundle[result.size()]);
    }

    private boolean isVerbose() {
        return (m_configuration.get("verbose") != null) && Boolean.parseBoolean(m_configuration.get("verbose"));
    }

    /**
     * Load {@link BundleProvider}s through the {@link ServiceLoader}.
     * 
     * @return list of providers
     * @throws Exception
     *             on failure
     */
    private BundleProvider[] loadBundleProviders() throws Exception {
        ServiceLoader<BundleProvider> bundleFactoryLoader = ServiceLoader.load(BundleProvider.class);
        Iterator<BundleProvider> bundleFactoryIterator = bundleFactoryLoader.iterator();
        List<BundleProvider> bundleFactoryList = new ArrayList<>();
        while (bundleFactoryIterator.hasNext()) {
            bundleFactoryList.add(bundleFactoryIterator.next());
        }
        return bundleFactoryList.toArray(new BundleProvider[bundleFactoryList.size()]);
    }

    private Properties loadDefaultProperties() throws IOException {
        InputStream inStream = null;
        try {
            inStream = getClass().getResourceAsStream("launcher-defaults.properties");
            Properties properties = new Properties();
            properties.load(inStream);
            return properties;
        }
        finally {
            close(inStream);
        }
    }

    private FrameworkFactory loadFrameworkFactory() throws IllegalStateException {
        ServiceLoader<FrameworkFactory> frameworkFactoryLoader = ServiceLoader.load(FrameworkFactory.class);
        Iterator<FrameworkFactory> frameworkFactoryIterator = frameworkFactoryLoader.iterator();
        if (!frameworkFactoryIterator.hasNext()) {
            throw new IllegalStateException("Unable to load any FrameworkFactory");
        }
        return frameworkFactoryIterator.next();
    }

    private Properties loadUserProperties(String configFile) throws IOException {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(configFile));
            return properties;
        }
        catch (Exception e) {
            System.err.println("Can not load or access configuration file : " + configFile);
            return null;
        }
    }

    private void printHelp(Options options) {
        // Since the main() method is in the same class, we can use the following trick to obtain the JAR name...
        String jarName;
        try {
            jarName = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
        }
        catch (URISyntaxException exception) {
            jarName = getClass().getSimpleName();
        }

        PrintWriter pw = new PrintWriter(System.out);

        new HelpFormatter().printHelp(pw, 120, "java -jar " + jarName, "\nOptions:", options, 4, 2, null, true);

        pw.println("\n" +
            "The configuration file provides the system and framework properties as well as the\n" +
            "agent configuration settings. If you specify a setting both on the command line and in\n" + 
            "a configuration file, the command line setting takes precedence. System properties can\n" + 
            "be specified by prefixing the property name with 'system.', for example, to set the\n" +
            "system property 'my.setting' one should specify it as 'system.my.setting'. All non-\n" +
            "system properties (including the agent configuration) are regarded as framework\n" +
            "properties, that is, will be passed to the OSGi framework.\n\n" +
            "Common options are (see ACE documentation for extensive list):\n\n" +
            "- agent.identification.agentid = <ID>\n" +
            "    defines the target name as <name>, if not given 'defaultTargetID' will be used.\n" +
            "    Note that the option '-a' overrides the agent ID in the configuration file!\n" +
            "- agent.discovery.serverurls = <URL-1>,<URL-2>,...,<URL-N>\n" +
            "    defines the location of the Apache ACE server(s), multiple servers can be given\n" +
            "    by separating their URLs with commas. Multiple URLs are used in best-effort round\n" +
            "    robin mode, that is, if the first URL fails, the second URL will be used, and so\n" +
            "    on. If not supplied, the default URL of 'http://localhost:8080' is used.\n" +
            "    Note that the option '-s' overrides the server URL(s) in the configuration file!\n" +
            "- agent.logging.level = (DEBUG|INFO|WARNING|ERROR)\n" +
            "    defines the log level of the agent. If not defined, 'INFO' is used as log level.\n" +
            "    Note that passing the option '-v' to the launcher implicitly sets the log level\n" +
            "    to 'DEBUG';\n" +
            "- agent.controller.syncinterval = <N>\n" +
            "    defines the synchronization interval (in seconds) in which the agent will\n" +
            "    synchronize its state with the Apache ACE server;\n" +
            "- agent.controller.syncdelay = <N>\n" +
            "    defines the initial delay (in seconds) before the agent will start synchronization\n" +
            "    with the Apache ACE server;\n" +
            "- launcher.bundles.dir = <PATH>\n" +
            "    defines the path where the launcher can find its initial set of bundles that should\n" +
            "    be installed upon startup of the agent. If not specified, the launcher will look at\n" +
            "    a directory called 'bundle' in the same directory as the launcher.\n");
        pw.flush();
        pw.close();
    }

    private void propagateConfiguration(Properties properties, Map<String, String> config) {
        if (properties == null) {
            return;
        }
        for (Object _key : properties.keySet()) {
            String key = (String) _key;
            String value = properties.getProperty(key);
            if (key.startsWith(SYSTEM_PREFIX)) {
                System.setProperty(key.substring(SYSTEM_PREFIX.length()), value);
            }
            else if (key.startsWith(FRAMEWORK_PREFIX)) {
                // Strip off the framework prefix, as we pass all configuration options as fw property...
                config.put(key.substring(FRAMEWORK_PREFIX.length()), value);
            }
            else {
                config.put(key, value);
            }
        }
    }

    private void propagateConfiguration(String[] properties, Map<String, String> config) {
        for (String property : properties) {
            String[] kv = property.split("\\s*=\\s*", 2);

            String key = kv[0].trim();
            String value = (kv.length > 1) ? kv[1].trim() : "";

            if (key.startsWith(SYSTEM_PREFIX)) {
                // Never let system properties to propagate as fw property...
                System.setProperty(key.substring(SYSTEM_PREFIX.length()), value);
            }
            else if (key.startsWith(FRAMEWORK_PREFIX)) {
                // Strip off the framework prefix, as we pass all configuration options as framework option...
                config.put(key.substring(FRAMEWORK_PREFIX.length()), value);
            }
            else {
                config.put(key, value);
            }
        }
    }

    private void logVerbose(String msg, Object... args) {
        if (isVerbose()) {
            System.out.printf(msg, args);
        }
    }
}
