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
package org.apache.ace.test.build;

import java.util.Collection;

import org.apache.ace.test.utils.TestUtils;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.Reporter;
import org.testng.TestListenerAdapter;

/**
 * Reports information about broken tests.
 */
public class BrokenTestListener extends TestListenerAdapter {

    @Override
    public void onStart(ITestContext testContext) {
        Collection<ITestNGMethod> excluded = testContext.getExcludedMethods();
        StringBuffer output = new StringBuffer();
        output.append("<h2>Broken tests</h2>\n");
        output.append("<table><tr><td>Name</td><td>Class</td><td>Groups</td><td>Description</td></tr>");
        for (ITestNGMethod m : excluded) {
            StringBuffer groups = new StringBuffer();
            boolean found = false;
            for (String g : m.getGroups()) {
                if (TestUtils.BROKEN.equals(g)) {
                    found = true;
                }
                else {
                    if (groups.length() > 0) {
                        groups.append(", ");
                    }
                    groups.append(g);
                }
            }
            if (found) {
                output.append("<tr>" +
            		"<td>" + m.getMethodName() + "</td>" +
    				"<td>" + m.getMethod().getDeclaringClass().getName() + "</td>" +
                    "<td>" + groups.toString() + "</td>" +
                    "<td>" + m.getDescription() + "</td>" +
					"</tr>\n");
            }
        }
        output.append("</table>\n");
        Reporter.log(output.toString());
    }
}
