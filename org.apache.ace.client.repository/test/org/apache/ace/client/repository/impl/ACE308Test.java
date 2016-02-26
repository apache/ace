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
package org.apache.ace.client.repository.impl;

import org.apache.ace.client.repository.stateful.impl.LogEventComparator;
import org.apache.ace.feedback.Event;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Before fixing ACE-308 the comparator could "overflow" when casting a long to an int, changing the sign of the result.
 * For this specific case, it would fail. After the fix, it no longer fails.
 */
public class ACE308Test {
    @Test
    public void testLogEvents() {
        LogEventComparator c = new LogEventComparator();
        Event left = new Event("t", 1, 1, -1000000000000000000L, 0);
        Event right = new Event("t", 1, 1, 1, 0);
        Assert.assertTrue((left.getTime() - right.getTime()) < 0L);
        Assert.assertTrue(c.compare(left, right) < 0L);
    }
}
