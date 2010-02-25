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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates over a <code>SortedRangeSet</code>. Does not exactly implement
 * the <code>Iterator</code> interface because we have <code>long</code>
 * primitives in our collection instead of full-blown objects. This iterator
 * is not thread-safe and results are unpredictable if the underlying set is
 * modified.
 */
public class RangeIterator {
    private final Iterator m_iterator;
    private Range m_current;
    private long m_number;
    
    RangeIterator(Iterator iterator) {
        m_iterator = iterator;
    }
    
    public boolean hasNext() {
        if (m_current == null) {
            return m_iterator.hasNext();
        }
        if (m_number == m_current.getHigh()) {
            return m_iterator.hasNext();
        }
        else {
            return true;
        }
    }
    
    public long next() {
        if (m_current == null) {
            if (m_iterator.hasNext()) {
                m_current = (Range) m_iterator.next();
                m_number = m_current.getLow();
                return m_number;
            }
        }
        else {
            if (m_number == m_current.getHigh()) {
                if (m_iterator.hasNext()) {
                    m_current = (Range) m_iterator.next();
                    m_number = m_current.getLow();
                    return m_number;
                }
            }
            else {
                m_number++;
                return m_number;
            }
        }
        throw new NoSuchElementException();
    }
}
