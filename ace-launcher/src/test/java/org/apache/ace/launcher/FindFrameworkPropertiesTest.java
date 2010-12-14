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

package org.apache.ace.launcher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

@RunWith(Parameterized.class)
public class FindFrameworkPropertiesTest {
    private final String[] m_args;
    private final Map<String, String> m_expected;

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new String[] {"--fwOption=something=true"}, map("something", "true") },
                { new String[] {"--fwOption=something=true", "--fwOption=somethingElse=false"}, map("something", "true", "somethingElse", "false") },
                { new String[] {"--fwOption=something=true", "--fwOption=something=false"}, map("something", "false") },
                { new String[] {"--fwOption=something=true=false"}, map("something", "true=false") },
                { new String[] {"--fwOption=some.thing=true"}, map("some.thing", "true") },
                { new String[] {"--fwOption=nothing"}, map() }
        });
    }

    public FindFrameworkPropertiesTest(String[] args, Map<String, String> expected) {
        m_args = args;
        m_expected = expected;
    }

    @Test
    public void test() {
        assertEquals(m_expected, Main.findFrameworkProperties(m_args));
    }

    /**
     * Helper method to create a literal map
     */
    private static <T> Map<T, T> map(T... values) {
        Map<T, T> result = new HashMap<T, T>();

        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i+1]);
        }

        return result;
    }
}
