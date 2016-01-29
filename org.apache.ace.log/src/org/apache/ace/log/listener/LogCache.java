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

/**
 * This cache is used whenever the real log service is not available. When the real log becomes available, all cached
 * log entries should be flushed to the real log service and leaving the cache empty afterwards.
 */
public class LogCache implements Log {

    private final List<LogEntry> m_logEntries = new CopyOnWriteArrayList<>();

    /**
     * Log the entry in the cache for flushing to the real log service later on.
     */
    public void log(int type, Dictionary properties) {
        m_logEntries.add(new LogEntry(type, properties));
    }

    /**
     * Flushes all cached log entries to the specified Log and leaves the cache empty after flushing. Will do nothing
     * when a null is passed as parameter.
     * 
     * @param log
     *            The log service to flush the cached log entries to
     */
    public void flushTo(Log log) {
        if (log == null) {
            // do nothing, as you want to keep using the cache
            return;
        }

        List<LogEntry> entries = new ArrayList<>(m_logEntries);
        m_logEntries.removeAll(entries);

        for (LogEntry entry : entries) {
            log.log(entry.getType(), entry.getProperties());
        }
    }

    private class LogEntry {
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
