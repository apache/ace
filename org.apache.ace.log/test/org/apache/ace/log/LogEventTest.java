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
package org.apache.ace.log;

import java.util.HashMap;
import java.util.Map;

import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Event;
import org.testng.annotations.Test;

public class LogEventTest {
    @Test()
    public void serializeLogEvent() {
        Event e = new Event("gwid", 1, 2, 3, AuditEvent.FRAMEWORK_STARTED);
        assert e.toRepresentation().equals("gwid,1,2,3," + AuditEvent.FRAMEWORK_STARTED);
        Map<String, String> p = new HashMap<>();
        p.put(AuditEvent.KEY_ID, "my first value");
        e = new Event("gwid", 1, 2, 3, AuditEvent.BUNDLE_INSTALLED, p);
        assert e.toRepresentation().equals("gwid,1,2,3," + AuditEvent.BUNDLE_INSTALLED + "," + AuditEvent.KEY_ID + ",my first value");
        e = new Event("gwid,gwid\n\r$", 1, 2, 3, AuditEvent.FRAMEWORK_STARTED);
        assert e.toRepresentation().equals("gwid$kgwid$n$r$$,1,2,3," + AuditEvent.FRAMEWORK_STARTED);
    }

    @Test()
    public void deserializeLogEvent() {
        Event e = new Event("gwid$kgwid$n$r$$,1,2,3," + AuditEvent.FRAMEWORK_STARTED + ",a,1,b,2,c,3");
        assert e.getTargetID().equals("gwid,gwid\n\r$") : "Target ID is not correctly parsed";
        assert e.getStoreID() == 1 : "Log ID is not correctly parsed";
        assert e.getID() == 2 : "ID is not correctly parsed";
        assert e.getTime() == 3 : "Time is not correctly parsed";
        assert e.getType() == AuditEvent.FRAMEWORK_STARTED : "Event type is wrong";
        Map<String, String> p = e.getProperties();
        assert p != null : "Properties are not correctly parsed";
        assert p.get("a").equals("1") : "Property a should be 1";
        assert p.get("b").equals("2") : "Property a should be 1";
        assert p.get("c").equals("3") : "Property a should be 1";
    }

    @Test()
    public void deserializeIllegalLogEvent() {
        try {
            new Event("garbage in, garbage out!");
            assert false : "Parsing garbage should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new Event("g$z,1,2,3," + AuditEvent.BUNDLE_STOPPED);
            assert false : "Parsing illegal token should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new Event("g$,1,2,3," + AuditEvent.BUNDLE_STOPPED);
            assert false : "Parsing half of a token should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new Event("g$,1,2,3," + AuditEvent.BUNDLE_STOPPED + ",a");
            assert false : "Parsing only a key should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
    }
}
