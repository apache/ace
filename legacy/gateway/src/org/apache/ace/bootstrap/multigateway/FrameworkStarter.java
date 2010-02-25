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
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Base class for classes that start specific OSGi frameworks.
 */
public class FrameworkStarter {
    private static final String KEYVALUE_SPLITTER = "=";

    private static final String RUNTIME_PROPERTY = "org.apache.ace.bootstrap.runtime";

    /** The list of bundles that will be installed and started during framework start up. */
    protected List m_bundlePaths = new ArrayList();

    /** The runtime directory of the framework */
    private String m_runtimeDir = "runtime";

    /**
     * Processes array of command line arguments to filter out the bundle locations. These are added to the member variable
     * <code>m_bundles</code>, the remaining args are returned.
     *
     * @param args the command line args
     * @return remaining args (command line args that still need to be processed)
     */
    protected String[] extractBundleLocations(String[] args) {
        if (m_bundlePaths == null) {
            m_bundlePaths = new ArrayList();
        }

        int i = 0;
        for (; i < args.length; i++) {
            File file = new File(args[i]);
            if (file.exists()) {
                if (file.isDirectory()) {
                    String[] jarFileList = file.list(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".jar");
                        }
                    });
                    for (int j = 0; j < jarFileList.length; j++) {
                        m_bundlePaths.add(args[i] + File.separator + jarFileList[j]);
                    }
                }
                else {
                    m_bundlePaths.add(args[i]);
                }
            }
            else {
                break;
            }
        }

        int extra = args.length - i;
        String[] remainingArgs = new String[extra];
        System.arraycopy(args, i, remainingArgs, 0, extra);
        return remainingArgs;
    }

    /**
     * Splits property definitions of the form key=value into a separate key and value.
     *
     * @param nameAndValue the property definition to split
     * @return array of two strings, first is key and second is value.
     */
    protected String[] splitKeyAndValue(String nameAndValue) {
        String[] retVal = new String[2];

        StringTokenizer tokenizer = new StringTokenizer(nameAndValue, KEYVALUE_SPLITTER);
        int tokens = tokenizer.countTokens();
        if (tokens != 2) {
            throw new IllegalArgumentException("Failed to parse command line argument.");
        }
        // Assume we have a correct name and value pair
        retVal[0] = tokenizer.nextToken();
        retVal[1] = tokenizer.nextToken();
        return retVal;
    }

    /**
     * Add the commandline arguments to a collection of properties. These arguments are split into key-value pairs
     *
     * @param properties the list of properties to add these arguments to
     * @param args the command line arguments
     * @param propertyPrefix the prefix to be used by the properties
     */
    protected void addProperties(Map properties, String[] args, String propertyPrefix) {
        if (propertyPrefix == null) {
            propertyPrefix = "";
        }
        for (int i = 0; i < args.length; i++) {
            String[] nameValuePair = splitKeyAndValue(args[i]);
            properties.put(propertyPrefix + nameValuePair[0], nameValuePair[1]);
        }
    }

    /**
     * Return the runtime directory of the framework. When a systemproperty is set, that directoryname is used instead of the
     * default "runtime"
     *
     * @return the runtime dir
     */
    protected String getRuntimeDirectory() {
        String systemProperty = System.getProperty(RUNTIME_PROPERTY);
        if ((systemProperty != null) && !systemProperty.equals("")) {
            return systemProperty;
        }
        return m_runtimeDir;
    }
}
