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
package org.apache.ace.client.repository.stateful.impl;

import java.util.Comparator;

import org.apache.ace.feedback.Event;

public final class LogEventComparator implements Comparator<Event> {
	public int compare(Event left, Event right) {
        if (left.getStoreID() == right.getStoreID()) {
            return sgn(left.getTime() - right.getTime());
        }
        else {
            return sgn(left.getStoreID() - right.getStoreID());
        }
    }

	public int sgn(long number) {
    	if (number < 0) {
    		return -1;
    	}
    	else if (number > 0) {
    		return 1;
    	}
    	return 0;
    }
}