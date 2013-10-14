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

package org.apache.ace.it.deployment;

import org.apache.ace.test.constants.TestConstants;

/**
 * Constants for use in the integration tests.
 */
public interface Constants {
    /**
     * Provides a simple authentication scheme for a single user 'd' (password 'f') that has all rights. The XML format
     * can be used by the "user admin configurator".
     */
    String TEST_AUTH_SCHEME = "<roles><group name=\"TestGroup\"><properties><type>userGroup</type></properties></group>"
        + "<user name=\"d\"><properties><username>d</username></properties><credentials>"
        + "<password>f</password></credentials><memberof>TestGroup</memberof></user></roles>";
    /**
     * Customer name for testing purposes.
     */
    String TEST_CUSTOMER = "apache";
    /**
     * The TCP port on which the HTTP server should run.
     */
    int TEST_HTTP_PORT = TestConstants.PORT;
    /**
     * Target ID for use in tests.
     */
    String TEST_TARGETID = "test-target";
}
