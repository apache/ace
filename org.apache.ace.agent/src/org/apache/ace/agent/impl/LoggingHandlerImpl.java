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

import java.util.Date;

import org.osgi.service.log.LogService;

/**
 * Internal logger that writes to system out for now. It minimizes work until it is determined the loglevel is loggable.
 */
public class LoggingHandlerImpl implements LoggingHandler {

    private final int m_level;

    public LoggingHandlerImpl(int level) {
        m_level = level;
    }

    private void log(String level, String component, String message, Throwable exception, Object... args) {
        if (args.length > 0)
            message = String.format(message, args);
        String line = String.format("[%s] %TT (%s) %s", level, new Date(), component, message);
        System.out.println(line);
        if (exception != null)
            exception.printStackTrace(System.out);
    }

    @Override
    public void logDebug(String component, String message, Throwable exception, Object... args) {
        if (m_level < LogService.LOG_DEBUG)
            return;
        log("DEBUG", component, message, exception, args);
    }

    @Override
    public void logInfo(String component, String message, Throwable exception, Object... args) {
        if (m_level < LogService.LOG_INFO)
            return;
        log("INFO", component, message, exception, args);
    }

    @Override
    public void logWarning(String component, String message, Throwable exception, Object... args) {
        if (m_level < LogService.LOG_WARNING)
            return;
        log("WARNING", component, message, exception, args);
    }

    @Override
    public void logError(String component, String message, Throwable exception, Object... args) {
        log("ERROR", component, message, exception, args);
    }

}
