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

import java.util.Dictionary;
import org.apache.ace.log.Log;

/**
 * Class responsible for being the object to talk to when trying to log events. This class
 * will decide whether to log it to cache, or to the actual log.
 */
public class LogProxy implements Log {

    private Log m_log;
    private LogCache m_logCache;

    public LogProxy() {
        m_logCache = new LogCache();
    }

    /**
     * Logs the log entry either to the real log service or to the cache, depending on
     * whether the real service is available.
     */
    public synchronized void log(int type, Dictionary properties) {
        if (m_log != null) {
            m_log.log(type, properties);
        }
        else {
            m_logCache.log(type, properties);
        }
    }

    /**
     * Sets the log, and flushes the cached log entries when a log service
     * is passed into this method. When null is passed as parameter, the log service
     * is not available anymore, and the cache should be used instead.
     * @param log  the log service to use, when null the cache will be used instead
     */
    public synchronized void setLog(Log log) {
        m_log = log;
        // flush content of the cache to the real log
        m_logCache.flushTo(m_log);
    }
}