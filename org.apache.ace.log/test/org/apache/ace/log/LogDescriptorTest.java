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

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.range.SortedRangeSet;
import org.testng.annotations.Test;

public class LogDescriptorTest {

    @Test()
    public void serializeDescriptor() {
        Descriptor descriptor = new Descriptor("gwid", 1, new SortedRangeSet("2-3"));
        assert descriptor.toRepresentation().equals("gwid,1,2-3") : "The representation of our descriptor is incorrect:" + descriptor.toRepresentation();
    }

    @Test()
    public void deserializeDescriptor() {
        Descriptor descriptor = new Descriptor("gwid,1,2-3");
        assert descriptor.getTargetID().equals("gwid") : "Target ID not correctly parsed.";
        assert descriptor.getStoreID() == 1 : "Log ID not correctly parsed.";
        assert descriptor.getRangeSet().toRepresentation().equals("2-3") : "There should be nothing in the diff between the set in the descriptor and the check-set.";
    }

    @Test()
    public void deserializeMultiRangeDescriptor() {
        Descriptor descriptor = new Descriptor("gwid,1,1-4$k6$k8$k10-20");
        assert descriptor.getTargetID().equals("gwid") : "Target ID not correctly parsed.";
        assert descriptor.getStoreID() == 1 : "Log ID not correctly parsed.";
        String representation = descriptor.getRangeSet().toRepresentation();
        assert representation.equals("1-4,6,8,10-20") : "There should be nothing in the diff between the set in the descriptor and the check-set, but we parsed: " + representation;
    }

    @Test()
    public void deserializeMultiRangeDescriptorWithFunnyGWID() {
        String line = "gw$$id,1,1-4$k6$k8$k10-20";
        Descriptor descriptor = new Descriptor(line);
        assert descriptor.getTargetID().equals("gw$id") : "Target ID not correctly parsed.";
        assert descriptor.getStoreID() == 1 : "Log ID not correctly parsed.";
        assert line.equals(descriptor.toRepresentation()) : "Converting the line back to the representation failed.";
        String representation = descriptor.getRangeSet().toRepresentation();
        assert representation.equals("1-4,6,8,10-20") : "There should be nothing in the diff between the set in the descriptor and the check-set, but we parsed: " + representation;
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void deserializeInvalidDescriptor() throws Exception {
        new Descriptor("invalidStringRepresentation");
    }
}
