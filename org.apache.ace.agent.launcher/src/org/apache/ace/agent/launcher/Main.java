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
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.agent.Constants;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.cm.ManagedService;

/**
 * A simple launcher, that launches the embedded Felix together with a management agent.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        Options options = new Options();
        options.addOption("v", "verbose", false, "verbose logging");
        options.addOption("h", "help", false, "print this message");

        CommandLineParser parser = new BasicParser();
        CommandLine command = parser.parse(options, args);

        if (command.hasOption("h")) {
            printHelp(options);
            return;
        }

        String[] arguments = command.getArgs();
        if (arguments.length > 1) {
            printHelp(options);
        }

        Properties defaultProperties = loadDefaultProperties();
        Properties userProperties = null;
        if (arguments.length == 1) {
            userProperties = loadUserProperties(arguments[0]);
            if (userProperties == null) {
                printHelp(options);
                return;
            }
        }

        Dictionary<String, String> configuration = new Hashtable<String, String>();

        // first map all default properties
        for (Object key : defaultProperties.keySet()) {
            configuration.put((String) key, defaultProperties.getProperty((String) key));
        }

        // overwrite with user properties
        if (userProperties != null) {
            for (Object key : userProperties.keySet()) {
                configuration.put((String) key, userProperties.getProperty((String) key));
            }
        }

        if (command.hasOption("v")) {
            configuration.put("verbose", "true");
        }

        new Main(configuration).run();
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(80);
        formatter.printHelp("java -jar org.apache.ace.agent.launcher [options] [configurationfile]", options);
    }

    private static Properties loadDefaultProperties() throws IOException {
        Properties properties = new Properties();
        ClassLoader classloader = Main.class.getClassLoader();
        InputStream inStream = classloader.getResourceAsStream("org/apache/ace/agent/launcher/launcher-defaults.properties");
        try {

            properties.load(inStream);
            return properties;
        }
        finally {
            inStream.close();
        }
    }

    private static Properties loadUserProperties(String configFileArgument) throws IOException {
        File configFile = new File(configFileArgument);
        if (!configFile.exists() || !configFile.isFile() || !configFile.canRead()) {
            System.err.println("Can not acces configuration file : " + configFileArgument);
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

    private final Dictionary<String, String> m_configuration;
    private final boolean m_verbose;

    public Main(Dictionary<String, String> configuration) {
        m_configuration = configuration;
        m_verbose = (m_configuration.get("verbose") != null) && Boolean.parseBoolean(m_configuration.get("verbose"));
    }

    public void run() throws Exception {

        try {
            Map<String, Object> frameworkProperties = createFrameworkProperties();
            if (m_verbose)
                System.out.println("Launching OSGi framework.. " + frameworkProperties);
            FrameworkFactory factory = createFrameworkFactory();
            Framework framework = factory.newFramework(frameworkProperties);
            framework.start();

            if (m_verbose)
                System.out.println("Configuring Management Agent.. " + m_configuration);
            BundleContext context = framework.getBundleContext();
            ServiceReference[] references = context.getServiceReferences(ManagedService.class.getName(), "(" + org.osgi.framework.Constants.SERVICE_PID + "=" + Constants.CONFIG_PID + ")");
            if (references != null) {
                ManagedService service = (ManagedService) context.getService(references[0]);
                service.updated(m_configuration);
                context.ungetService(references[0]);
            }
            else {
                System.err.println("Can not find Management Agent config service! Aborting..");
                System.exit(1);
            }

            if (m_verbose)
                System.out.println("Startup complete..");
            framework.waitForStop(0);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private FrameworkFactory createFrameworkFactory() throws Exception {
        String factoryClass = "org.apache.felix.framework.FrameworkFactory";
        try {
            Class<?> clazz = Class.forName(factoryClass);
            return (FrameworkFactory) clazz.newInstance();
        }
        catch (Exception e) {
            throw new Exception("Failed to create framework factory: " + factoryClass, e);
        }
    }

    private Map<String, Object> createFrameworkProperties() throws Exception {

        Map<String, Object> frameworkProperties = new HashMap<String, Object>();
        Enumeration<String> keyEnumeration = m_configuration.keys();
        while (keyEnumeration.hasMoreElements()) {
            String key = keyEnumeration.nextElement();
            if (key.startsWith("framework.")) {
                String frameworkKey = key.replaceFirst("framework.", "");
                String frameworkValue = m_configuration.get(key);
                frameworkProperties.put(frameworkKey, frameworkValue);
            }
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<Object> result = new ArrayList<Object>();
        try {
            Object instance = cl.loadClass("org.apache.ace.agent.impl.Activator").newInstance();
            result.add(instance);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        frameworkProperties.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP, result);
        return frameworkProperties;
    }
}
