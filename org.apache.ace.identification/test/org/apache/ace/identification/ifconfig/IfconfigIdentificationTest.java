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

import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class IfconfigIdentificationTest {

    private IfconfigIdentification m_identification;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_identification = new IfconfigIdentification();
        TestUtils.configureObject(m_identification, LogService.class);
    }

    @Test
    public void testMacAddressVerifying() throws Exception {
        assert m_identification.isValidMac("FF:FF:FF:FF:FF:FF");
        assert m_identification.isValidMac("01:23:45:67:89:01");
        assert m_identification.isValidMac("0D:C3:45:6A:B9:01");
        assert !m_identification.isValidMac("");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:F");
        assert !m_identification.isValidMac("A:B:C:D:E:F");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:FG");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:FF:");
        assert !m_identification.isValidMac("FF-FF-FF-FF-FF-FF");
        assert !m_identification.isValidMac("thisisnotamacaddr");
    }
}
