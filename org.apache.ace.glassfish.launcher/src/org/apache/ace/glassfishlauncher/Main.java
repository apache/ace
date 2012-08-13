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
package org.apache.ace.glassfishlauncher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A simple launcher, that launches Glassfish and installs the Management Agent.
 */
public class Main {
    private static final int BUFFER_SIZE = 4096;
    private String m_identification;
    private String m_discovery;

    private Argument m_identificationArgument = new KeyValueArgument() {
        @Override
        public void handle(final String key, final String value) {
            if (key.equals("identification")) {
                m_identification = value;
            }
        }

        public String getDescription() {
            return "identification: sets the target ID to use";
        }
    };

    private Argument m_discoveryArgument = new KeyValueArgument() {
        @Override
        public void handle(final String key, final String value) {
            if (key.equals("discovery")) {
                m_discovery = value;
            }
        }

        public String getDescription() {
            return "discovery: sets the ACE server to connect to";
        }
    };

    private Argument m_agentsArgument = new KeyValueArgument() {
        @Override
        public void handle(final String key, final String value) {
            if (key.equals("agents")) {
                System.setProperty("agents", value);
            }
        }

        public String getDescription() {
            return "agents: configures multiple management agents: agent-id,identification,discovery[;agent-id,identification,discovery]*";
        }
    };

    private Argument m_helpArgument = new Argument() {
        public void handle(final String argument) {
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
            m_identificationArgument,
            m_discoveryArgument,
            m_agentsArgument,
            m_fwOptionHandler,
            m_helpArgument);

    public static void main(final String[] args) throws Exception {
        new Main(args).run();
    }

    public Main(final String[] args) {
        for (String arg : args) {
            for (Argument argument : m_arguments) {
                argument.handle(arg);
            }
        }
    }

    public void run() throws Exception {
        unzip();
        runCommand("chmod 755 glassfish3/bin/asadmin");
        replaceDomainXml();
        String bundledir = "glassfish3/glassfish/modules/autostart/";
        copyResource("ace-managementagent.jar", bundledir + "ace-managementagent.jar");
        copyClasspathResource("osgi.properties", "glassfish3/glassfish/config/osgi.properties");
        runCommand("glassfish3/bin/asadmin start-domain domain1");
    }

    private void replaceDomainXml() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("domain.xml");
        StringBuilder fileContents = new StringBuilder();
        Scanner scanner = new Scanner(inputStream);
        String lineSeparator = System.getProperty("line.separator");

        try {
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine()).append(lineSeparator);
            }
        }
        finally {
            scanner.close();
            inputStream.close();
        }

        String domainXML = fileContents.toString()
            .replace("GLASSFISHIDENTITY", m_identification)
            .replace("GLASSFISHPORT", "8080")
            .replace("DISCOVERY", m_discovery);

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream("glassfish3/glassfish/domains/domain1/config/domain.xml");
            fileOutputStream.write(domainXML.getBytes());
        }
        finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    private void copyResource(final String resource, final String to) throws IOException {
        InputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new FileInputStream(resource);
            out = new BufferedOutputStream(new FileOutputStream(to));
    
            copy(in, out);
        }
        finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    private void copy(InputStream inputStream, OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
    }

    private void copyClasspathResource(final String resource, final String to) throws IOException {
        InputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = getClass().getClassLoader().getResourceAsStream(resource);
            out = new BufferedOutputStream(new FileOutputStream(to));
            copy(in, out);
        }
        finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public void runCommand(final String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        StreamReader in = new StreamReader(process.getInputStream());
        StreamReader err = new StreamReader(process.getErrorStream());
        in.start();
        err.start();
    }

    /**
     * Thread that can be run in the background to keep reading from a stream.
     */
    private static class StreamReader extends Thread {
        private final InputStream m_in;
        
        public StreamReader(InputStream is) {
            super("StreamReader");
            setDaemon(true);
            m_in = is;
        }
        
        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(m_in);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ( (line = br.readLine()) != null) {}
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    
    private void unzip() throws IOException {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream("glassfish.zip")));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    new File(entry.getName()).mkdir();
                }
                else {
                    BufferedOutputStream dest = null;
                    try {
                        dest = new BufferedOutputStream(new FileOutputStream(entry.getName()));
                        copy(zis, dest);
                    }
                    finally {
                        if (dest != null) {
                            dest.flush();
                            dest.close();
                        }
                    }
                }
            }
        }
        finally {
            if (zis != null) {
                zis.close();
            }
        }
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

    private static interface Argument {
        void handle(String argument);
        String getDescription();
    }

    private static abstract class KeyValueArgument implements Argument {
        public void handle(final String argument) {
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
        protected void handle(final String key, final String value) {
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
    }
}
