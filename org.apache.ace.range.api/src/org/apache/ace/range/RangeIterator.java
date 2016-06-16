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

import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Iterates over a <code>SortedRangeSet</code>. Does not exactly implement
 * the <code>Iterator</code> interface because we have <code>long</code>
 * primitives in our collection instead of full-blown objects. This iterator
 * is not thread-safe and results are unpredictable if the underlying set is
 * modified.
 */
@ProviderType
public class RangeIterator {
    private final ListIterator m_iterator;
    private Range m_current;
    private long m_number;
    private boolean m_reverseOrder;

    RangeIterator(ListIterator iterator, boolean reverseOrder) {
        m_iterator = iterator;
        m_reverseOrder = reverseOrder;
    }

    public boolean hasNext() {
        return m_reverseOrder ? hasPreviousElement() : hasNextElement();
    }
    
    public long next() {
        return m_reverseOrder ? previousElement() : nextElement();
    }
    
    private boolean hasNextElement() {
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
    
    private boolean hasPreviousElement() {
        if (m_current == null) {
            return m_iterator.hasPrevious();
        }
        if (m_number == m_current.getLow()) {
            return m_iterator.hasPrevious();
        }
        else {
            return true;
        }
    }

    private long nextElement() {
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

    private long previousElement() {
        if (m_current == null) {
            if (m_iterator.hasPrevious()) {
                m_current = (Range) m_iterator.previous();
                m_number = m_current.getHigh();
                return m_number;
            }
        }
        else {
            if (m_number == m_current.getLow()) {
                if (m_iterator.hasPrevious()) {
                    m_current = (Range) m_iterator.previous();
                    m_number = m_current.getHigh();
                    return m_number;
                }
            }
            else {
                m_number--;
                return m_number;
            }
        }
        throw new NoSuchElementException();
    }
}