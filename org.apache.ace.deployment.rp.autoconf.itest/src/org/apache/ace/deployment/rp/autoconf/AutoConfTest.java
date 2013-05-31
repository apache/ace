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
package org.apache.ace.deployment.rp.autoconf;

import org.apache.ace.it.IntegrationTestBase;
import org.osgi.framework.Constants;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;

public class AutoConfTest extends IntegrationTestBase {

    /**
     * A basic test that validates the rp is available and thus everything resolved.
     * 
     * @throws Exception
     */
    public void testAvailable() throws Exception {
        ResourceProcessor rp = getService(ResourceProcessor.class, "(" + Constants.SERVICE_PID + "=org.osgi.deployment.rp.autoconf)");
        assertNotNull("Expected to be able to locate the Autoconf ResourceProcessor", rp);
    }
}
