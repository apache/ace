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

import java.util.Enumeration;
import java.util.Properties;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class PropertiesConverter implements Converter {

    @Override
    public boolean canConvert(Class clazz) {
        return Properties.class.isAssignableFrom(clazz);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Properties properties = new Properties();
        
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            properties.put(reader.getNodeName(), reader.getValue());
            reader.moveUp();
        }
        
        return properties;
    }

    @Override
    public void marshal(Object propertiesObject, HierarchicalStreamWriter writer, MarshallingContext context) {
        if (propertiesObject == null ){
            return;
        }
        
        Properties properties = (Properties) propertiesObject;
        if (properties.isEmpty()){
            return;
        }
        
        Enumeration<?> propertyNames = properties.propertyNames();
        while (propertyNames.hasMoreElements()) {
            String name = (String) propertyNames.nextElement();
            String value = (String) properties.getProperty(name);
            
            writer.startNode(name);
            writer.setValue(value);
            writer.endNode();
            
        }
    }
}