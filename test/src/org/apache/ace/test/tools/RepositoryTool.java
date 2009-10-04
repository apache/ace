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
package org.apache.ace.test.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.repository.RangeIterator;
import org.apache.ace.repository.SortedRangeSet;
import org.apache.ace.test.constants.TestConstants;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;

/**
 * Command line tool to access repositories.
 */
public class RepositoryTool {
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final int COPY_BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        Parser parser = new PosixParser();
        Options options = new Options();
        options.addOption("h", "host", true, "Host URL of the repository");
        options.addOption("c", "command", true, "Command to send (query, commit, checkout, get, put, backup, restore)");
        options.addOption("C", "customer", true, "Customer ID");
        options.addOption("n", "name", true, "Repository name");
        options.addOption("v", "version", true, "Version");
        options.addOption("f", "file", true, "File or directory");

        Properties props = new Properties();
        try {
            props.load(new FileInputStream(new File(".repository")));
        }
        catch (FileNotFoundException e) {
            // if the file does not exist, simply ignore it
        }
        catch (IOException e) {
            System.err.println("I/O exception while reading defaults in .repository: " + e);
        }
        try {
            CommandLine line = parser.parse(options, args, props);
            String host = line.getOptionValue("host", "http://localhost:" + TestConstants.PORT + "/");
            String cmd = line.getOptionValue("command", "query");
            String customer = line.getOptionValue("customer");
            String name = line.getOptionValue("name");
            String version = line.getOptionValue("version", "0");
            String file = line.getOptionValue("file", "file");
            try {
                if ("query".equals(cmd)) {
                    query(host, customer, name);
                }
                else if ("commit".equals(cmd)) {
                    put("repository/commit", host, customer, name, version, file);
                }
                else if ("checkout".equals(cmd)) {
                    get("repository/checkout", host, customer, name, version, file);
                }
                else if ("put".equals(cmd)) {
                    put("replication/put", host, customer, name, version, file);
                }
                else if ("get".equals(cmd)) {
                    get("replication/get", host, customer, name, version, file);
                }
                else if ("backup".equals(cmd)) {
                    backup(host, customer, name, file);

                }
                else if ("restore".equals(cmd)) {
                    restore(host, customer, name, file);
                }
                else {
                    showHelp(options);
                }
            }
            catch (IOException e) {
                System.err.println("Could not connect to host URL: " + e);
            }
            catch (Exception e) {
                System.err.println("Error: " + e);
            }
        }
        catch (ParseException exp) {
            System.err.println("Unexpected exception:" + exp.getMessage());
            showHelp(options);
        }
    }

    private static void restore(String host, String customer, String name, String file) throws MalformedURLException, IOException, FileNotFoundException {
        File dir = new File(file);
        if (!dir.isDirectory()) {
            System.err.println("Backup directory does not exist: " + file);
            System.exit(5);
        }
        File[] versions = dir.listFiles();
        if (versions != null) {
            for (int i = 0; i < versions.length; i++) {
                put("replication/put", host, customer, name, versions[i].getName(), versions[i].getAbsolutePath());
            }
        }
    }

    private static void backup(String host, String customer, String name, String file) throws MalformedURLException, IOException {
        File dir = new File(file);
        if (!dir.isDirectory()) {
            if (!dir.mkdirs()) {
                System.err.println("Could not make backup directory " + file);
                System.exit(5);
            }
        }
        URL query = new URL(new URL(host), "replication/query" + "?customer=" + customer + "&name=" + name);
        HttpURLConnection connection = (HttpURLConnection) query.openConnection();
        if (connection.getResponseCode() == HttpServletResponse.SC_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            try {
                String l = reader.readLine();
                int i = l.lastIndexOf(',');
                if (i > 0) {
                    SortedRangeSet remoteRange = new SortedRangeSet(l.substring(i + 1));
                    RangeIterator iterator = remoteRange.iterator();
                    while (iterator.hasNext()) {
                        String v = Long.toString(iterator.next());
                        String f = (new File(dir, v)).getAbsolutePath();
                        get("replication/get", host, customer, name, v, f);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("Error parsing remote range " + e);
                System.exit(5);
            }
        }
        else {
            System.err.println("Could not make backup for customer " + customer + " name " + name);
            System.exit(5);
        }
    }

    private static void query(String host, String customer, String name) throws MalformedURLException, IOException {
        String f1 = (customer == null) ? null : "customer=" + customer;
        String f2 = (name == null) ? null : "name=" + name;
        String filter = ((f1 == null) ? "?" : "?" + f1 + "&") + ((f2 == null) ? "" : f2);
        URL url = new URL(new URL(host), "repository/query" + filter);
        URLConnection connection = url.openConnection();
        InputStream input = connection.getInputStream();
        copy(input, System.out);
    }

    private static void get(String endpoint, String host, String customer, String name, String version, String file) throws MalformedURLException, IOException, FileNotFoundException {
        URL url = new URL(new URL(host), endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);
        URLConnection connection = url.openConnection();
        InputStream input = connection.getInputStream();
        OutputStream out = new FileOutputStream(file);
        copy(input, out);
        out.flush();
    }

    private static void put(String endpoint, String host, String customer, String name, String version, String file) throws MalformedURLException, IOException, FileNotFoundException {
        URL url = new URL(new URL(host), endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", MIME_APPLICATION_OCTET_STREAM);
        OutputStream out = connection.getOutputStream();
        InputStream input = new FileInputStream(file);
        copy(input, out);
        out.flush();
        InputStream is = (InputStream) connection.getContent();
        is.close();
    }

    private static void showHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("RepositoryTool", options);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }
}
