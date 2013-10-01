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
package org.apache.ace.feedback.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class Codec
{

    public static String decode(String source) throws IllegalArgumentException {
        StringBuffer result = new StringBuffer();
        StringCharacterIterator sci = new StringCharacterIterator(source);
        for (char c = sci.current(); c != CharacterIterator.DONE; c = sci.next()) {
            if (c == '$') {
                c = sci.next();
                if (c != CharacterIterator.DONE) {
                    if (c == '$') {
                        result.append('$');
                    }
                    else if (c == 'k') {
                        result.append(',');
                    }
                    else if (c == 'n') {
                        result.append('\n');
                    }
                    else if (c == 'r') {
                        result.append('\r');
                    }
                    else if (c == 'e') {
                        return null;
                    }
                    else {
                        throw new IllegalArgumentException("Unknown escape character: " + c);
                    }
                }
                else {
                    throw new IllegalArgumentException("Unexpected end of input: " + source);
                }
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String encode(String source) {
        if (source == null) {
            return "$e";
        }
        StringBuffer result = new StringBuffer();
        StringCharacterIterator sci = new StringCharacterIterator(source);
        for (char c = sci.current(); c != CharacterIterator.DONE; c = sci.next()) {
            if (c == '$') {
                result.append("$$");
            }
            else if (c == ',') {
                result.append("$k");
            }
            else if (c == '\n') {
                result.append("$n");
            }
            else if (c == '\r') {
                result.append("$r");
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }
}