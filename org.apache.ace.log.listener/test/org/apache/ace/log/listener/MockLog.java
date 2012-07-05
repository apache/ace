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
package org.apache.ace.log.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;

import org.apache.ace.log.Log;

public class MockLog implements Log {

    @SuppressWarnings("unchecked")
    private List m_logEntries;

    @SuppressWarnings("unchecked")
    public MockLog() {
        m_logEntries = Collections.synchronizedList(new ArrayList());
    }

    @SuppressWarnings("unchecked")
    public void log(int type, Dictionary properties) {
        m_logEntries.add(new LogEntry(type, properties));
    }

    @SuppressWarnings("unchecked")
    public List getLogEntries() {
        return new ArrayList(m_logEntries);
    }

    public void clear() {
        m_logEntries.clear();
    }

    public class LogEntry {
        private int m_type;
        @SuppressWarnings("unchecked")
        private Dictionary m_properties;
        @SuppressWarnings("unchecked")
        public LogEntry(int type, Dictionary properties) {
            m_type = type;
            m_properties = properties;
        }

        public int getType() {
            return m_type;
        }

        @SuppressWarnings("unchecked")
        public Dictionary getProperties() {
            return m_properties;
        }
    }
}
