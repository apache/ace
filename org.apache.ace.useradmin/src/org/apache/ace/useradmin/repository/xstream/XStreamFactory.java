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
package org.apache.ace.useradmin.repository.xstream;

import com.thoughtworks.xstream.XStream;

public class XStreamFactory {

    private XStreamFactory() {
        // Not used
    }
    
    public static XStream getInstance() {
        XStream xStream = new XStream();
        xStream.alias("group", GroupDTO.class);
        xStream.alias("user", UserDTO.class);
        xStream.useAttributeFor(RoleDTO.class, "name");
        xStream.addImplicitCollection(RoleDTO.class, "memberOf", "memberof", String.class);
        
        xStream.registerConverter(new PropertiesConverter(), 10);
        xStream.omitField(RoleDTO.class, "type");
        return xStream;
    }
    
}
