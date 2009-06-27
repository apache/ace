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
package org.apache.ace.repository;

/**
 * Class that captures a simple, modifiable range.
 */
public class Range {
    private long m_low;
    private long m_high;
    
    public Range(String representation) {
        int i = representation.indexOf('-');
        if (i == -1) {
            m_low = m_high = Long.parseLong(representation);
        }
        else {
            long low = Long.parseLong(representation.substring(0, i));
            long high = Long.parseLong(representation.substring(i + 1));
            if (low <= high) {
                m_low = low;
                m_high = high;
            }
            else {
                throw new IllegalArgumentException("illegal range");
            }
        }
    }
    
    public Range(long number) {
        m_low = m_high = number;
    }
    
    public Range(long low, long high) {
        if (low <= high) {
            m_low = low;
            m_high = high;
        }
        else {
            throw new IllegalArgumentException("illegal range");
        }
    }
    
    public long getLow() {
        return m_low;
    }
    
    public void setLow(long low) {
        m_low = low;
        if (m_high < m_low) {
            m_high = m_low;
        }
    }

    public long getHigh() {
        return m_high;
    }
    
    public void setHigh(long high) {
        m_high = high;
        if (m_low > m_high) {
            m_low = m_high;
        }
    }
    
    public boolean contains(long number) {
        return (m_low <= number) && (m_high >= number);
    }
    
    public String toRepresentation() {
        if (m_low == m_high) {
            return Long.toString(m_low);
        }
        else {
            return Long.toString(m_low) + '-' + Long.toString(m_high);
        }
    }
}
