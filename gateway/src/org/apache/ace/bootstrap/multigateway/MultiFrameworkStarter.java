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
package org.apache.ace.bootstrap.multigateway;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.osgi.framework.Bundle;

/**
 * A bootstrap class for the Felix OSGi framework that can be used to create multiple instances in a single VM.
 */
public class MultiFrameworkStarter extends FrameworkStarter {

    /**
     * The start-character for commented lines
     */
    public static final String COMMENT_START = "#";

    private static final String RUNTIME_PROPERTY = "org.apache.ace.bootstrap.runtime";

    /**
     * The system property name used to specify an URL to the configuration property file to be used for the created the
     * framework instance.
     */
    public static final String CONFIG_PROPERTIES_PROP = "felix.config.properties";

    /**
     * The default name used for the configuration properties file.
    **/
    public static final String CONFIG_PROPERTIES_FILE_VALUE = "config.properties";

    /**
     * Starts a Felix framework instance.
     *
     * @param args command line arguments containing name value pairs to set as arguments
     */
    public void start(String[] args) throws Exception {
        String[] otherArgs = extractBundleLocations(args);
        String bundleList = "";
        for (int i = 0; i < m_bundlePaths.size(); i++) {
            bundleList += "reference:file:" + m_bundlePaths.get(i) + " ";
        }
        Map properties = new HashMap();
        Properties systemProps = loadConfigProperties();
        for (Enumeration e = systemProps.keys();e.hasMoreElements();) {
            String key = (String) e.nextElement();
            properties.put(key, systemProps.get(key));
        }
        properties.put("felix.auto.start.1", bundleList);
        properties.put("felix.startlevel.framework", "1");
        properties.put("felix.startlevel.bundle", "1");
        properties.put("felix.embedded.execution", "true");
        addProperties(properties, otherArgs, null);

        System.setSecurityManager(null);
        // instantiate Felix using reflection, because that saves us from having to
        // put the Felix implementation on the compiler classpath: the instance
        // implements the Bundle interface, which is enough
        Class c = Class.forName("org.apache.felix.framework.Felix");
        Constructor cc = c.getConstructor(new Class[] { Map.class });
        Bundle felix = (Bundle) cc.newInstance(new Object[] { properties });
        felix.start();
    }

    /**
     * Command line way to start #instances Felix frameworks.
     *
     * @param args the first arguments that denote an existing file or directory, are considered to be bundle locations for
     *        bundles that must be installed and started at startup. If a parameter instances=<name>(,<name>)* is present the
     *        named instances are created.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("instances=")) {
                // put all instance names in an array
                StringTokenizer tok = new StringTokenizer(args[i].substring("instances=".length()), ",");
                String[] instances = new String[tok.countTokens()];
                for (int icount = 0; icount < instances.length; icount++) {
                    instances[icount] = tok.nextToken().trim();
                }

                // put all arguments in list and remove the instances= argument
                List argList = new ArrayList(Arrays.asList(args));
                argList.remove(i);

                // start instances
                for (int icount = 0; icount < instances.length; icount++) {
                    argList.add("org.osgi.framework.storage=" + getRuntimeDirectory(instances[icount]));
                    argList.add("configuredGatewayID=" + instances[icount]);
                    (new MultiFrameworkStarter()).start((String[]) argList.toArray(new String[argList.size()]));
                    argList.remove(argList.size() - 1);
                    argList.remove(argList.size() - 1);
                }
                return;
            }
        }
        System.err.println("Usage: <bundle-dir> instances=<name>(,<name>)* ");
        System.exit(1);
    }

    private static String getRuntimeDirectory(String postfix) {
        String systemProperty = System.getProperty(RUNTIME_PROPERTY);
        if ((null == systemProperty) || systemProperty.equals("")) {
            return postfix;
        }
        return systemProperty + File.separator + postfix;
    }

    /**
     * <p>
     * Loads the configuration properties in the configuration property file associated with the framework installation; these
     * properties are accessible to the framework and to bundles and are intended for configuration purposes. By default, the
     * configuration property file is located in the <tt>conf/</tt> directory of the  installation directory and is
     * called "<tt>config.properties</tt>". The installation directory of Felix is assumed to be the directory of the
     * <tt>multigatewaybootstrap.jar</tt> file as found on the system class path property. The precise file from which to load configuration
     * properties can be set by initializing the "<tt>felix.config.properties</tt>" system property to an arbitrary URL.
     * </p>
     *
     * @return A <tt>Properties</tt> instance or <tt>null</tt> if there was an error.
     */
    public static Properties loadConfigProperties() {
        // The config properties file is either specified by a system
        // property or it is in the conf/ directory of the Felix
        // installation directory.  Try to load it from one of these
        // places.

        // See if the property URL was specified as a property.
        URL propURL = null;
        String custom = System.getProperty(CONFIG_PROPERTIES_PROP);
        if (custom != null) {
            try {
                propURL = new URL(custom);
            }
            catch (MalformedURLException ex) {
                System.err.print("Main: " + ex);
                return null;
            }
        }
        else {
            // Determine where the configuration directory is by figuring
            // out where felix.jar is located on the system class path.
            File confDir = null;
            String classpath = System.getProperty("java.class.path");
            int index = classpath.toLowerCase().indexOf("multigatewaybootstrap.jar");
            int start = classpath.lastIndexOf(File.pathSeparator, index) + 1;
            if (index >= start) {
                // Get the path of the felix.jar file.
                String jarLocation = classpath.substring(start, index);
                // Calculate the conf directory based on the parent
                // directory of the felix.jar directory.
                confDir = new File(new File(new File(jarLocation).getAbsolutePath()), "conf");
            }
            else {
                // Can't figure it out so use the current directory as default.
                confDir = new File(System.getProperty("user.dir"));
            }

            try {
                propURL = new File(confDir, CONFIG_PROPERTIES_FILE_VALUE).toURL();
            }
            catch (MalformedURLException ex) {
                System.err.print("Main: " + ex);
                return null;
            }
        }

        // Read the properties file.
        Properties props = new Properties();
        InputStream is = null;
        try {
            is = propURL.openConnection().getInputStream();
            props.load(is);
            is.close();
        }
        catch (FileNotFoundException ex) {
            // Ignore file not found.
        }
        catch (Exception ex) {
            System.err.println("Error loading config properties from " + propURL);
            System.err.println("Main: " + ex);
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException ex2) {
                // Nothing we can do.
            }
            return null;
        }

        // Perform variable substitution for system properties.
        for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            props.setProperty(name, substVars(props.getProperty(name), name, null, props));
        }

        return props;
    }

    private static final String DELIM_START = "${";

    private static final String DELIM_STOP = "}";

    /**
     * <p>
     * This method performs property variable substitution on the specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt> refers to either a configuration property or a
     * system property, then the corresponding property value is substituted for the variable placeholder. Multiple variable
     * placeholders may exist in the specified value as well as nested variable placeholders, which are substituted from inner
     * most to outer most. Configuration properties override system properties.
     * </p>
     *
     * @param val The string on which to perform property substitution.
     * @param currentKey The key of the property being evaluated used to detect cycles.
     * @param cycleMap Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the property placeholder syntax or a recursive variable
     *         reference.
     */
    public static String substVars(String val, String currentKey, Map cycleMap, Properties configProps) throws IllegalArgumentException {
        // If there is currently no cycle map, then create
        // one for detecting cycles for this invocation.
        if (cycleMap == null) {
            cycleMap = new HashMap();
        }

        // Put the current key in the cycle map.
        cycleMap.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int stopDelim = val.indexOf(DELIM_STOP);

        // Find the matching starting "${" variable delimiter
        // by looping until we find a start delimiter that is
        // greater than the stop delimiter we have found.
        int startDelim = val.indexOf(DELIM_START);
        while (stopDelim >= 0) {
            int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
            if ((idx < 0) || (idx > stopDelim)) {
                break;
            }
            else if (idx < stopDelim) {
                startDelim = idx;
            }
        }

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        if ((startDelim < 0) && (stopDelim < 0)) {
            return val;
        }
        // At this point, we found a stop delimiter without a start,
        // so throw an exception.
        else if (((startDelim < 0) || (startDelim > stopDelim)) && (stopDelim >= 0)) {
            throw new IllegalArgumentException("stop delimiter with no start delimiter: " + val);
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

        // Verify that this is not a recursive variable reference.
        if (cycleMap.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        String substValue = (configProps != null) ? configProps.getProperty(variable, null) : null;
        if (substValue == null) {
            // Ignore unknown property values.
            substValue = System.getProperty(variable, "");
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycleMap.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length(), val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = substVars(val, currentKey, cycleMap, configProps);

        // Return the value.
        return val;
    }
}
