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
package org.apache.ace.range;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Class that captures a simple, modifiable range.
 */
@ProviderType
public class Range {
    private long m_low;
    private long m_high;

    /**
     * Create a new range based on a string representation of that range.
     *
     * @param representation the string representation
     */
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

    /**
     * Create a range that consists of a single number.
     *
     * @param number the number
     */
    public Range(long number) {
        m_low = m_high = number;
    }

    /**
     * Creates a range from a lower to a higher bound.
     *
     * @param low the lower bound
     * @param high the higher bound
     */
    public Range(long low, long high) {
        if (low <= high) {
            m_low = low;
            m_high = high;
        }
        else {
            throw new IllegalArgumentException("illegal range");
        }
    }

    /**
     * Returns the lower bound.
     *
     * @return the lower bound
     */
    public long getLow() {
        return m_low;
    }

    /**
     * Sets a new lower bound. Will make sure the range stays valid,
     * so if the higher bound is smaller than the new lower bound, it will
     * be made equal to this new lower bound.
     *
     * @param low the new lower bound
     */
    public void setLow(long low) {
        m_low = low;
        if (m_high < m_low) {
            m_high = m_low;
        }
    }

    /**
     * Returns the higher bound.
     *
     * @return the higher bound
     */
    public long getHigh() {
        return m_high;
    }

    /**
     * Sets a new higher bound. Will make sure the range stays valid,
     * so if the lower bound is bigger than the new higher bound, it will
     * be made equal to this new higher bound.
     *
     * @param high the new higher bound
     */
    public void setHigh(long high) {
        m_high = high;
        if (m_low > m_high) {
            m_low = m_high;
        }
    }

    /**
     * Checks if a number falls within this range.
     *
     * @param number the number to check
     * @return <code>true</code> if the number was inside the range
     */
    public boolean contains(long number) {
        return (m_low <= number) && (m_high >= number);
    }

    /**
     * Converts the range to a string representation that can be parsed
     * back to a new <code>Range</code> object.
     * 
     * @return string representation
     */
    public String toRepresentation() {
        if (m_low == m_high) {
            return Long.toString(m_low);
        }
        else {
            return Long.toString(m_low) + '-' + Long.toString(m_high);
        }
    }
    
    @Override
    public String toString() {
        return "Range[" + toRepresentation() + "]";
    }
}