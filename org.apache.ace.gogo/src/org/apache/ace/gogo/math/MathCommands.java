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
package org.apache.ace.gogo.math;

import org.apache.felix.service.command.Descriptor;

public class MathCommands {

    public final static String SCOPE = "math";
    public final static String[] FUNCTIONS = new String[] { "lt", "gt", "eq", "inc", "dec", "add", "sub" };

    @Descriptor("test if first number is greater then second number")
    public static boolean gt(long first, long second) {
        return first > second;
    }

    @Descriptor("test if first number is greater then second number")
    public static boolean lt(long first, long second) {
        return first < second;
    }

    @Descriptor("test if first number is equal to second number")
    public static boolean eq(long first, long second) {
        return first == second;
    }

    @Descriptor("returns the given number incremented by one")
    public static long inc(long num) {
        return num + 1;
    }

    @Descriptor("returns the given number decremented by one")
    public static long dec(long num) {
        return num - 1;
    }

    @Descriptor("returns the addition of two numbers")
    public static long add(long a, long b) {
        return a + b;
    }

    @Descriptor("returns the difference of two numbers")
    public static long sub(long a, long b) {
        return a - b;
    }
}
