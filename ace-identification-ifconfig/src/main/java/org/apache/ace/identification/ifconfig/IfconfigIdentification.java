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
package org.apache.ace.identification.ifconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.ace.identification.Identification;
import org.osgi.service.log.LogService;

/**
 * Implementation of the <code>Identification</code> interface which will determine a mac-address based ID which is determined
 * by running the ifconfig command. The first adapter that has been assigned an ip address is used.
 *
 * The identification has been tested on <code>ifconfig 1.42 (2001-04-13)</code> which comes with Debian Linux. Similar
 * versions of ifconfig are likely to work.
 */
public class IfconfigIdentification implements Identification {

    private static final String IFCONFIG_COMMAND = "ifconfig";
    private static final String MAC_IDENTIFIER = "HWaddr ";
    private static final String IP_IDENTIFIER = "inet addr";

    private volatile LogService m_log; // injected by dependency manager

    private String m_targetID = null;

    public synchronized String getID() {
        if (m_targetID == null) {
            BufferedReader reader = null;
            try {
                Process process = Runtime.getRuntime().exec(IFCONFIG_COMMAND);
                InputStream inputStream = process.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                m_targetID = parseIfconfigOutput(reader).toLowerCase();
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_WARNING, "Unable to determine ifconfig based mac-address target identification.", ioe);
                return null;
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    }
                    catch (IOException e) {
                        // not much we can do
                    }
                }
            }
        }
        return m_targetID;
    }

    /**
     * Parses the mac address of the first active adapter from the output of the ifconfig command.
     *
     * @param ifconfigOutput Reader pointing to the output of the ifconfig command.
     * @return String containing the mac address or <code>null</code> if no valid mac address could be determined.
     * @throws java.io.IOException If the specified reader could not be read correctly.
     */
    protected String parseIfconfigOutput(BufferedReader ifconfigOutput) throws IOException {
        // Sample output (the part that matters):
        // eth0      Link encap:Ethernet  HWaddr 00:00:21:CF:76:47
        //           inet addr:192.168.1.65  Bcast:192.168.1.255  Mask:255.255.255.0
        String previousLine = "";
        String line;
        while ((line = ifconfigOutput.readLine()) != null) {
            if (line.indexOf(IP_IDENTIFIER) != -1) {
                if (previousLine.indexOf(MAC_IDENTIFIER) != -1) {
                    String macAddress = previousLine.substring(previousLine.lastIndexOf(MAC_IDENTIFIER) + MAC_IDENTIFIER.length(), previousLine.length());
                    macAddress = macAddress.trim();
                    if (isValidMac(macAddress)) {
                        return macAddress;
                    }
                    else {
                        return null;
                    }
                }
            }
            previousLine = line;
        }
        return null;
    }

    /**
     * Verifies whether a string contains a valid mac addres, a valid mac address consists of
     * 6 pairs of [A-F,a-f,0-9] separated by ':', e.g. <code>0A:F6:33:19:DE:2A</code>.
     *
     * @param mac String containing the possible mac address
     * @return true If the specified string contains a valid mac address, false otherwise.
     */
    protected boolean isValidMac(String mac) {
        if (mac.length() != 17) {
            return false;
        }
        char[] chars = mac.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i % 3 == 2) {
                if (':' != c) {
                    return false;
                }
            }
            else if (!(('0' <= c) && (c <= '9')) &&
                     !(('a' <= c) && (c <= 'f')) &&
                     !(('A' <= c) && (c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}