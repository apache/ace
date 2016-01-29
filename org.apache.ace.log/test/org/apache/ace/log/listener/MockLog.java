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
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.ace.log.Log;

public class MockLog implements Log {

    private List<LogEntry> m_logEntries;

    public MockLog() {
        m_logEntries = new CopyOnWriteArrayList<>();
    }

    public void log(int type, Dictionary properties) {
        m_logEntries.add(new LogEntry(type, properties));
    }

    public List<LogEntry> getLogEntries() {
        return new ArrayList<>(m_logEntries);
    }

    public void clear() {
        m_logEntries.clear();
    }

    public class LogEntry {
        private int m_type;
        private Dictionary<String, ?> m_properties;

        public LogEntry(int type, Dictionary<String, ?> properties) {
            m_type = type;
            m_properties = properties;
        }

        public int getType() {
            return m_type;
        }

        public Dictionary<String, ?> getProperties() {
            return m_properties;
        }
    }
}
