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

import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;
import org.testng.annotations.Test;

public class PropertyBasedIdentificationTest {
    @Test(groups = { UNIT })
    public void getIdWithoutUpdate() {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        assertThat( basedIdentification.getID(), is( nullValue() ) );
    }

    @Test(groups = { UNIT })
    public void getIdWithUpdate() throws ConfigurationException {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        Dictionary dict = new Hashtable();
        dict.put( IdentificationConstants.IDENTIFICATION_TARGETID_KEY, "myGatewayId" );
        basedIdentification.updated( dict );
        assertThat( basedIdentification.getID(), is( equalTo( "myGatewayId" ) ) );
    }

    @Test(groups = { UNIT })
    public void getIdOverwrite() throws ConfigurationException {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        injectServices( basedIdentification );

        Dictionary dict = new Hashtable();
        dict.put( IdentificationConstants.IDENTIFICATION_TARGETID_KEY, "oldId" );
        Dictionary dict2 = new Hashtable();
        dict2.put( IdentificationConstants.IDENTIFICATION_TARGETID_KEY, "newId" );

        basedIdentification.updated( dict );
        basedIdentification.updated( dict2 );

        assertThat( basedIdentification.getID(), is( equalTo( "newId" ) ) );
    }

    private void injectServices(Object o) {
        for (Field field : o.getClass().getDeclaredFields()) {
            if (field.getType() == LogService.class) {
                field.setAccessible(true);
                try {
                    field.set(o, getLogService());
                }
                catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private LogService getLogService() {
        return mock(LogService.class);
    }
}
