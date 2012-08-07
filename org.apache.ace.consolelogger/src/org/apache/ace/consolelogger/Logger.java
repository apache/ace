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
package org.apache.ace.consolelogger;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * An implementation of the OSGi LogService that directly outputs each log message
 * to <code>System.out</code>. It does not implement the LogReader or LogListeners.
 */
public class Logger implements LogService {
    private static String[] LEVEL = { "", "Error", "Warn ", "Info ", "Debug" };

    public void log(int level, String message) {
        log(null, level, message, null);
    }

    public void log(int level, String message, Throwable throwable) {
        log(null, level, message, throwable);
    }

    public void log(ServiceReference reference, int level, String message) {
        log(reference, level, message, null);
    }

    public void log(ServiceReference reference, int level, String message, Throwable throwable) {
        String bundle = " [   ]";
        String service = " ";
        if (reference != null) {
            bundle = "00" + reference.getBundle().getBundleId();
            bundle = " [" + bundle.substring(bundle.length() - 3) + "]";
            Object objectClass = reference.getProperty(Constants.OBJECTCLASS);
            if (objectClass instanceof String[]) {
                StringBuffer buffer = new StringBuffer();
                String[] objClassArr = ((String[]) objectClass);
                for (int i = 0; i < objClassArr.length; i++) {
                    String svc = objClassArr[i];
                    if (buffer.length() > 0) {
                        buffer.append(';');
                    }
                    buffer.append(svc);
                    service = buffer.toString() + ": ";
                }
            }
            else {
                service = objectClass.toString() + ": ";
            }
        }
        System.out.println("[" + LEVEL[level] + "]" + bundle + service + message);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
    }
}