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
package org.apache.ace.processlauncher.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a string on spaces and puts them into an array, taking care of single and double quotes.
 * Escaping quotes is allowed by prefixing them with a single backslash.
 */
public final class StringSplitter {

    private static final char ESCAPE = '\\';
    private static final char SINGLE_QUOTE = '\'';
    private static final char DOUBLE_QUOTE = '"';
    private static final char SPACE = ' ';
    private static final char TAB = '\t';

    /**
     * Creates a new StringSplitter instance, never used.
     */
    private StringSplitter() {
        // No-op
    }

    /**
     * Splits a given input on whitespace (= tabs and/or spaces). The backslash character can be
     * used to escape stuff, for example to avoid splitting on certain spaces.
     * 
     * @param input the input to split, may be <code>null</code>.
     * @return an empty array if the given input was <code>null</code> or empty, otherwise the split
     *         results, never <code>null</code>.
     */
    public static String[] split(String input) {
        return split(input, true /* includeQuotes */);
    }

    /**
     * Splits a given input on whitespace (= tabs and/or spaces). The backslash character can be
     * used to escape stuff, for example to avoid splitting on certain spaces.
     * 
     * @param input the input to split, may be <code>null</code>;
     * @param includeQuotes <code>true</code> if quotes should be included in the output,
     *        <code>false</code> to omit them in the output unless they are escaped.
     * @return an empty array if the given input was <code>null</code> or empty, otherwise the split
     *         results, never <code>null</code>.
     */
    public static String[] split(String input, boolean includeQuotes) {
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }

        List<String> result = new ArrayList<String>();

        State state = State.NORMAL;
        boolean escapeSeen = false;
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            switch (ch) {
                case ESCAPE:
                    if (!escapeSeen) {
                        escapeSeen = true;
                    }
                    else {
                        // Escaped escape character...
                        token.append(ch);
                        escapeSeen = false;
                    }
                    break;

                case SINGLE_QUOTE:
                case DOUBLE_QUOTE:
                    if (!escapeSeen) {
                        if ((ch == DOUBLE_QUOTE && state.isInDoubleQuote())
                            || (ch == SINGLE_QUOTE && state.isInSingleQuote())) {
                            state = State.NORMAL;
                        }
                        else if (state.isNormal()) {
                            state = (ch == DOUBLE_QUOTE) ? State.IN_DOUBLE_QUOTE : State.IN_SINGLE_QUOTE;
                        }
                    }
                    if (includeQuotes) {
                        token.append(ch);
                    }
                    escapeSeen = false;
                    break;

                case SPACE:
                case TAB:
                    if (state.isNormal() && !escapeSeen) {
                        // Whitespace seen: emit a new token and start over...
                        result.add(token.toString());
                        token.setLength(0);
                        break;
                    }
                    // Fallthrough!
                default:
                    token.append(ch);
                    escapeSeen = false;
                    break;
            }
        }

        if (token.length() > 0) {
            result.add(token.toString());
        }

        return result.toArray(new String[result.size()]);
    }

    private static enum State {
        NORMAL, IN_SINGLE_QUOTE, IN_DOUBLE_QUOTE;

        public boolean isInDoubleQuote() {
            return this == IN_DOUBLE_QUOTE;
        }

        public boolean isInSingleQuote() {
            return this == IN_SINGLE_QUOTE;
        }

        public boolean isNormal() {
            return this == NORMAL;
        }
    }
}
