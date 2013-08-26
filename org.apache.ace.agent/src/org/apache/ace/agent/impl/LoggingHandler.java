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
package org.apache.ace.agent.impl;

import java.util.Formatter;

import org.apache.ace.agent.AgentContext;

/**
 * Agent context delegate interface that is responsible for logging. This is an internal interface as log methods are
 * exposed on the {@link AgentContext} directly.
 */
public interface LoggingHandler {

    /**
     * Log an debug message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logDebug(String component, String message, Throwable exception, Object... args);

    /**
     * Log an info message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logInfo(String component, String message, Throwable exception, Object... args);

    /**
     * Log an warning message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logWarning(String component, String message, Throwable exception, Object... args);

    /**
     * Log an error message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logError(String component, String message, Throwable exception, Object... args);
}
