/*
 * Copyright 2009 Toni Menzel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ace.identification.property;

import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.junit.Test;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsEqual.*;
import static org.hamcrest.core.IsNull.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Toni Menzel
 * @since Dec 7, 2009
 */
public class PropertyBasedIdentificationTest
{

    @Test
    public void getIdWithoutUpdate()
    {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        assertThat( basedIdentification.getID(), is( nullValue() ) );
    }

    @Test
    public void getIdWithUpdate()
        throws ConfigurationException
    {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        Dictionary dict = new Hashtable();
        dict.put( IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, "myGatewayId" );
        basedIdentification.updated( dict );
        assertThat( basedIdentification.getID(), is( equalTo( "myGatewayId" ) ) );
    }

    @Test
    public void getIdOverwrite()
        throws ConfigurationException
    {
        PropertyBasedIdentification basedIdentification = new PropertyBasedIdentification();
        injectServices( basedIdentification );

        Dictionary dict = new Hashtable();
        dict.put( IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, "oldId" );
        Dictionary dict2 = new Hashtable();
        dict2.put( IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, "newId" );

        basedIdentification.updated( dict );
        basedIdentification.updated( dict2 );

        assertThat( basedIdentification.getID(), is( equalTo( "newId" ) ) );
    }

    private void injectServices( Object o )
    {
        for( Field field : o.getClass().getDeclaredFields() )
        {
            if( field.getType() == LogService.class )
            {
                field.setAccessible( true );
                try
                {
                    field.set( o, getLogService() );
                } catch( IllegalAccessException e )
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private LogService getLogService()
    {
        return mock( LogService.class );

    }
}
