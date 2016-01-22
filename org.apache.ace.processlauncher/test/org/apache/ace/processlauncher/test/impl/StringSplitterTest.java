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
package org.apache.ace.processlauncher.test.impl;

import static org.apache.ace.processlauncher.impl.StringSplitter.split;
import static org.apache.ace.test.utils.TestUtils.UNIT;
import static org.testng.Assert.assertEquals;

import org.apache.ace.processlauncher.impl.StringSplitter;
import org.testng.annotations.Test;

/**
 * Test cases for {@link StringSplitter}.
 */
public class StringSplitterTest {

    /**
     * Test double quoted command line argument.
     */
    @Test(groups = { UNIT })
    public void testDoubleQuotedCommandLineArgumentOk() {
        String[] result =
            split("\\\"fwOption=org.osgi.framework.system.packages.extra=org.w3c.dom.tral,org.w3c.dom.html,org.w3c.dom.ranges,sun.reflect,org.osgi.service.deploymentadmin;version=\"1.0\",org.osgi.service.deploymentadmin.spi;version=\"1.0\",org.osgi.service.cm;version=\"1.3\",org.osgi.service.event;version=\"1.2\",org.osgi.service.log;version=\"1.3\",org.osgi.service.metatype;version=\"1.1\",org.apache.ace.log;version=\"0.8.0\"\\\"");
        assertArrayEquals(
            new String[] { "\"fwOption=org.osgi.framework.system.packages.extra=org.w3c.dom.tral,org.w3c.dom.html,org.w3c.dom.ranges,sun.reflect,org.osgi.service.deploymentadmin;version=\"1.0\",org.osgi.service.deploymentadmin.spi;version=\"1.0\",org.osgi.service.cm;version=\"1.3\",org.osgi.service.event;version=\"1.2\",org.osgi.service.log;version=\"1.3\",org.osgi.service.metatype;version=\"1.1\",org.apache.ace.log;version=\"0.8.0\"\"" },
            result);
    }

    /**
     * Test double quoted string.
     */
    @Test(groups = { UNIT })
    public void testDoubleQuotedStringOk() {
        String[] result = split("\"hello world\"");
        assertArrayEquals(new String[] { "\"hello world\"" }, result);
    }

    /**
     * Test double quoted string with trailing text.
     */
    @Test(groups = { UNIT })
    public void testDoubleQuotedStringWithTrailingTextOk() {
        String[] result = split("\"hello world\" foo-bar");
        assertArrayEquals(new String[] { "\"hello world\"", "foo-bar" }, result);
    }

    /**
     * Test double quoted words.
     */
    @Test(groups = { UNIT })
    public void testDoubleQuotedWordsOk() {
        String[] result = split("\"hello\" \"world\"");
        assertArrayEquals(new String[] { "\"hello\"", "\"world\"" }, result);
    }

    /**
     * Test double quoted words omit quotes.
     */
    @Test(groups = { UNIT })
    public void testDoubleQuotedWordsOmitQuotesOk() {
        String[] result = split("\"hello\" \"world\"", false /* includeQuotes */);
        assertArrayEquals(new String[] { "hello", "world" }, result);
    }

    /**
     * Test escaped backslash in string.
     */
    @Test(groups = { UNIT })
    public void testEscapedBackslashInStringOk() {
        String[] result = split("hello\\\\ world");
        assertArrayEquals(new String[] { "hello\\", "world" }, result);
    }

    /**
     * Test escaped backslash string in double quotes.
     */
    @Test(groups = { UNIT })
    public void testEscapedBackslashStringInDoubleQuotesOk() {
        String[] result = split("\"hello\\\\ world\"");
        assertArrayEquals(new String[] { "\"hello\\ world\"" }, result);
    }

    /**
     * Test escaped double quoted in single quoted string.
     */
    @Test(groups = { UNIT })
    public void testEscapedDoubleQuotedInSingleQuotedStringOk() {
        String[] result = split("'\"hello world\"'");
        assertArrayEquals(new String[] { "'\"hello world\"'" }, result);
    }

    /**
     * Test escaped double quoted string.
     */
    @Test(groups = { UNIT })
    public void testEscapedDoubleQuotedStringOk() {
        String[] result = split("\\\"hello world\\\"");
        assertArrayEquals(new String[] { "\"hello", "world\"" }, result);
    }

    /**
     * Test escaped key value pair.
     */
    @Test(groups = { UNIT })
    public void testEscapedKeyValuePairOk() {
        String[] result = split("key=\\'qux qoo\\'");
        assertArrayEquals(new String[] { "key='qux", "qoo'" }, result);
    }

    /**
     * Test escaped single quoted in double quoted string.
     */
    @Test(groups = { UNIT })
    public void testEscapedSingleQuotedInDoubleQuotedStringOk() {
        String[] result = split("\"\\'hello world\\'\"");
        assertArrayEquals(new String[] { "\"'hello world'\"" }, result);
    }

    /**
     * Test escaped single quoted string.
     */
    @Test(groups = { UNIT })
    public void testEscapedSingleQuotedStringOk() {
        String[] result = split("\\'hello world\\'");
        assertArrayEquals(new String[] { "\'hello", "world\'" }, result);
    }

    /**
     * Test escaped space string in double quotes.
     */
    @Test(groups = { UNIT })
    public void testEscapedSpaceStringInDoubleQuotesOk() {
        String[] result = split("\"hello\\ world\"");
        assertArrayEquals(new String[] { "\"hello world\"" }, result);
    }

    /**
     * Test escaped space string.
     */
    @Test(groups = { UNIT })
    public void testEscapedSpaceStringOk() {
        String[] result = split("hello\\ world");
        assertArrayEquals(new String[] { "hello world" }, result);
    }

    /**
     * Test key value pair in double quotes.
     */
    @Test(groups = { UNIT })
    public void testKeyValuePairInDoubleQuotesOk() {
        String[] result = split("\"key=\\\"qux qoo\\\"\"");
        assertArrayEquals(new String[] { "\"key=\"qux qoo\"\"" }, result);
    }

    /**
     * Test key value pair.
     */
    @Test(groups = { UNIT })
    public void testKeyValuePairOk() {
        String[] result = split("key='qux qoo'");
        assertArrayEquals(new String[] { "key='qux qoo'" }, result);
    }

    /**
     * Test os gi import package value.
     */
    @Test(groups = { UNIT })
    public void testOSGiImportPackageValueOk() {
        String[] result = split("\"org.foo.bar;version=\"1\",org.qux.quu;version=\"2\"\"");
        assertArrayEquals(new String[] { "\"org.foo.bar;version=\"1\",org.qux.quu;version=\"2\"\"" }, result);
    }

    /**
     * Test single quoted string.
     */
    @Test(groups = { UNIT })
    public void testSingleQuotedStringOk() {
        String[] result = split("'hello world'");
        assertArrayEquals(new String[] { "'hello world'" }, result);
    }

    /**
     * Test single quoted words.
     */
    @Test(groups = { UNIT })
    public void testSingleQuotedWordsOk() {
        String[] result = split("'hello' 'world'");
        assertArrayEquals(new String[] { "'hello'", "'world'" }, result);
    }

    /**
     * Test single quoted words omit quotes.
     */
    @Test(groups = { UNIT })
    public void testSingleQuotedWordsOmitQuotesOk() {
        String[] result = split("'hello' 'world'", false /* includeQuotes */);
        assertArrayEquals(new String[] { "hello", "world" }, result);
    }

    /**
     * Test split empty string.
     */
    @Test(groups = { UNIT })
    public void testSplitEmptyStringOk() {
        String[] result = split("");
        assertArrayEquals(new String[0], result);
    }

    /**
     * Test split null value.
     */
    @Test(groups = { UNIT })
    public void testSplitNullValueOk() {
        String[] result = split(null);
        assertArrayEquals(new String[0], result);
    }

    /**
     * Test split on tab.
     */
    @Test(groups = { UNIT })
    public void testSplitOnTabOk() {
        String[] result = split("hello\tworld");
        assertArrayEquals(new String[] { "hello", "world" }, result);
    }

    /**
     * Test split whitespaces only.
     */
    @Test(groups = { UNIT })
    public void testSplitWhitespacesOnlyOk() {
        String[] result = split(" \t  ");
        assertArrayEquals(new String[0], result);
    }

    /**
     * Test unquoted command line argument.
     */
    @Test(groups = { UNIT })
    public void testUnquotedCommandLineArgumentOk() {
        String[] result =
            split("fwOption=org.osgi.framework.system.packages.extra=org.w3c.dom.tral,org.w3c.dom.html,org.w3c.dom.ranges,sun.reflect,org.osgi.service.deploymentadmin;version=\"1.0\",org.osgi.service.deploymentadmin.spi;version=\"1.0\",org.osgi.service.cm;version=\"1.3\",org.osgi.service.event;version=\"1.2\",org.osgi.service.log;version=\"1.3\",org.osgi.service.metatype;version=\"1.1\",org.apache.ace.log;version=\"0.8.0\"");
        assertArrayEquals(
            new String[] { "fwOption=org.osgi.framework.system.packages.extra=org.w3c.dom.tral,org.w3c.dom.html,org.w3c.dom.ranges,sun.reflect,org.osgi.service.deploymentadmin;version=\"1.0\",org.osgi.service.deploymentadmin.spi;version=\"1.0\",org.osgi.service.cm;version=\"1.3\",org.osgi.service.event;version=\"1.2\",org.osgi.service.log;version=\"1.3\",org.osgi.service.metatype;version=\"1.1\",org.apache.ace.log;version=\"0.8.0\"" },
            result);
    }

    /**
     * Test unquoted string.
     */
    @Test(groups = { UNIT })
    public void testUnquotedStringOk() {
        String[] result = split("hello world");
        assertArrayEquals(new String[] { "hello", "world" }, result);
    }

    /**
     * Assert array equals.
     * 
     * @param expected the expected
     * @param actual the actual
     */
    private void assertArrayEquals(Object[] expected, Object[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
