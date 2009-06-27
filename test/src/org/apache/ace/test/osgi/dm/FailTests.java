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
package org.apache.ace.test.osgi.dm;

import org.testng.annotations.Test;

/**
 * A test class that will always fail. It is used as a dummy class to signal that
 * in fact the whole suite of tests did not run. This is usually caused by a test
 * environment not starting up completely (such as dependencies failing).
 */
public class FailTests {
    @SuppressWarnings("unchecked")
    private static Class[] CLASSES;
    private static String REASON;

    @SuppressWarnings("unchecked")
    public static void setClasses(Class[] classes) {
        CLASSES = classes;
    }

    public static void setReason(String reason) {
        REASON = reason;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void fail() {
        StringBuffer tests = new StringBuffer();
        for (Class c : CLASSES) {
            if (tests.length() > 0) {
                tests.append(", ");
            }
            tests.append(c.getName());
        }
        assert false : ("Failed to run test classes: " + tests.toString() + " because: " + REASON);
    }
}
