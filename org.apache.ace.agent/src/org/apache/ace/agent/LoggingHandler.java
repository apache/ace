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
package org.apache.ace.agent;

import java.util.Formatter;

import org.osgi.service.log.LogService;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Agent context delegate interface that is responsible for logging.
 */
@ConsumerType
public interface LoggingHandler {

    enum Levels {
        /** Lowest log level. Used to log debugging/internal information. */
        DEBUG(LogService.LOG_DEBUG),
        /** Used to log useful information. */
        INFO(LogService.LOG_INFO),
        /** Used to report warnings. */
        WARNING(LogService.LOG_WARNING),
        /** Highest log level, used to report errors. */
        ERROR(LogService.LOG_ERROR);

        private final int m_logLevel;

        private Levels(int logLevel) {
            m_logLevel = logLevel;
        }

        /**
         * @return the OSGi LogService log level corresponding to this log level.
         */
        public int getLogLevel() {
            return m_logLevel;
        }
    }

    /**
     * Log an debug message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component
     *            The component identifier, not <code>null</code>
     * @param message
     *            The log message or format, not <code>null</code>
     * @param cause
     *            The cause, may be <code>null</code>
     * @param args
     *            The optional formatter arguments
     */
    void logDebug(String component, String message, Throwable exception, Object... args);

    /**
     * Log an info message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component
     *            The component identifier, not <code>null</code>
     * @param message
     *            The log message or format, not <code>null</code>
     * @param cause
     *            The cause, may be <code>null</code>
     * @param args
     *            The optional formatter arguments
     */
    void logInfo(String component, String message, Throwable exception, Object... args);

    /**
     * Log an warning message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component
     *            The component identifier, not <code>null</code>
     * @param message
     *            The log message or format, not <code>null</code>
     * @param cause
     *            The cause, may be <code>null</code>
     * @param args
     *            The optional formatter arguments
     */
    void logWarning(String component, String message, Throwable exception, Object... args);

    /**
     * Log an error message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component
     *            The component identifier, not <code>null</code>
     * @param message
     *            The log message or format, not <code>null</code>
     * @param cause
     *            The cause, may be <code>null</code>
     * @param args
     *            The optional formatter arguments
     */
    void logError(String component, String message, Throwable exception, Object... args);
}
