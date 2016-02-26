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
package org.apache.ace.identification.property;

import java.util.Hashtable;

import org.apache.ace.identification.IdentificationConstants;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class SimpleIdentificationTest {
    private PropertyBasedIdentification m_identification;

    private static final String TEST_ID = "testTargetID";

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_identification = new PropertyBasedIdentification();
        TestUtils.configureObject(m_identification, LogService.class);
    }

    /**
     * Test simple identification
     *
     * @throws Exception
     */
    @SuppressWarnings("serial")
    @Test
    public void testSimpleIdentification() throws Exception {
        m_identification.updated(
            new Hashtable<String, Object>() {
                {put(IdentificationConstants.IDENTIFICATION_TARGETID_KEY, TEST_ID);}
            });
        assert TEST_ID.equals(m_identification.getID()) : "target ID does not match configured target ID";
    }
}
