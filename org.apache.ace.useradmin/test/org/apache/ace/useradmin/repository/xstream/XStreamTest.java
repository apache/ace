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

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.testng.annotations.Test;

import com.thoughtworks.xstream.XStream;

public class XStreamTest {

    @Test
    public void testRead() throws Exception {
        XStream xStream = XStreamFactory.getInstance();

        try (Reader reader = new FileReader(new File("test/valid.xml"));
            ObjectInputStream objectInputStream = xStream.createObjectInputStream(reader)) {

            GroupDTO testgroup = (GroupDTO) objectInputStream.readObject();
            assertEquals(testgroup.name, "testgroup");
            assertEquals(testgroup.properties.get("type"), "testGroupType");
            assertEquals(testgroup.properties.get("other"), "otherTestProperty");

            GroupDTO testgroup2 = (GroupDTO) objectInputStream.readObject();
            assertEquals(testgroup2.name, "testgroup2");
            assertEquals(testgroup2.properties.get("type"), "otherGroupType");
            assertEquals(testgroup2.memberOf, Arrays.asList("testgroup"));

            UserDTO testuser = (UserDTO) objectInputStream.readObject();
            assertEquals(testuser.name, "testuser");
            assertEquals(testuser.properties.get("username"), "testuser");
            assertEquals(testuser.credentials.get("password"), "test");
            assertEquals(testuser.memberOf, Arrays.asList("testgroup2"));
        }
    }

    @Test
    public void testWrite() throws Exception {
        XStream xStream = XStreamFactory.getInstance();
        StringWriter sw = new StringWriter();

        try (ObjectOutputStream objectOutputStream = xStream.createObjectOutputStream(sw, "roles")) {

            objectOutputStream.writeObject(
                new GroupDTO("testgroup", properties("type", "testGroupType", "other", "otherTestProperty"), null, null));
            objectOutputStream.writeObject(
                new GroupDTO("testgroup2", properties("type", "otherGroupType"), null, Arrays.asList("testgroup")));
            objectOutputStream.writeObject(
                new UserDTO("testuser", properties("username", "testuser"), properties("password", "test"), Arrays.asList("testgroup2")));
        }

        String outputString = sw.toString();

        String validXmlFileString = new String(Files.readAllBytes(Paths.get("test/valid.xml")));
        // Remove the comment...
        validXmlFileString = validXmlFileString.replaceAll("<!--[^\r\n]+-->[\r\n]+", "");

        assertEquals(outputString, validXmlFileString);
    }

    private static Properties properties(String... pairs) {
        Properties properties = new Properties();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            properties.put(pairs[i], pairs[i + 1]);
        }
        return properties;
    }

}
