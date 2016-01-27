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
package org.apache.ace.test.utils;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

public class Util {

    /**
     * Creates a Properties object from a list of key-value pairs, e.g.
     * <pre>
     * properties("key", "value", "key2", "value2");
     * </pre>
     */
    public static Properties properties(String... values) {
        Properties props = new Properties();
        for (int i = 0; i < values.length; i += 2) {
            props.put(values[i], values[i+1]);
        }
        return props;
    }

    /**
     * Creates a Dictionary object from a list of key-value pairs, e.g.
     * <pre>
     * properties("key", "value", "key2", "value2");
     * </pre>
     */
    public static Dictionary<String, Object> dictionary(String... values) {
        Dictionary<String, Object>  props = new Hashtable<>();
        for (int i = 0; i < values.length; i += 2) {
            props.put(values[i], values[i+1]);
        }
        return props;
    }
}
