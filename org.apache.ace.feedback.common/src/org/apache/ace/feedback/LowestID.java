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
package org.apache.ace.feedback;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.ace.feedback.util.Codec;

/**
 * Instances of this class represent a lowest ID for a specific target and store ID.
 */
public class LowestID {
	private final String m_targetID;
    private final long m_storeID;
    private final long m_lowestID;

	public LowestID(String targetID, long storeID, long lowestID) {
		m_targetID = targetID;
		m_storeID = storeID;
		m_lowestID = lowestID;
	}
	
	public LowestID(String representation) {
        try {
            StringTokenizer st = new StringTokenizer(representation, ",");
            m_targetID = Codec.decode(st.nextToken());
            m_storeID = Long.parseLong(st.nextToken());
            m_lowestID = Long.parseLong(st.nextToken());
        }
        catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Could not create lowest ID object from: " + representation);
        }
	}

	public String getTargetID() {
		return m_targetID;
	}

	public long getStoreID() {
		return m_storeID;
	}

	public long getLowestID() {
		return m_lowestID;
	}
	
    public String toRepresentation() {
        StringBuffer result = new StringBuffer();
        result.append(Codec.encode(m_targetID));
        result.append(',');
        result.append(m_storeID);
        result.append(',');
        result.append(m_lowestID);
        return result.toString();
    }
}
