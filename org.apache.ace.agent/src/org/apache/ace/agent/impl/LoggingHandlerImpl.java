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

import static org.apache.ace.agent.AgentConstants.CONFIG_LOGGING_LEVEL;

import java.util.Date;

import org.apache.ace.agent.LoggingHandler;

/**
 * Default thread-safe {@link LoggingHandler} implementation that logs messages to {@link System.out} .
 */
public class LoggingHandlerImpl extends ComponentBase implements LoggingHandler {

    public LoggingHandlerImpl() {
        super("logging");
    }

    @Override
    public void logDebug(String component, String message, Throwable exception, Object... args) {
        Levels level = getLogLevel();
        if (level == Levels.DEBUG) {
            log(Levels.DEBUG.name(), component, message, exception, args);
        }
    }

    @Override
    public void logInfo(String component, String message, Throwable exception, Object... args) {
        Levels level = getLogLevel();
        if (level == Levels.DEBUG || level == Levels.INFO) {
            log(Levels.INFO.name(), component, message, exception, args);
        }
    }

    @Override
    public void logWarning(String component, String message, Throwable exception, Object... args) {
        Levels level = getLogLevel();
        if (level == Levels.DEBUG || level == Levels.INFO || level == Levels.WARNING) {
            log(Levels.WARNING.name(), component, message, exception, args);
        }
    }

    @Override
    public void logError(String component, String message, Throwable exception, Object... args) {
        log(Levels.ERROR.name(), component, message, exception, args);
    }

    private void log(String level, String component, String message, Throwable exception, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        String line = String.format("[%s] %TT (%s) %s", level, new Date(), component, message);
        System.out.println(line);
        if (exception != null) {
            exception.printStackTrace(System.out);
        }
    }

    // TODO performance; replace with configuration events
    private Levels getLogLevel() {
        String config = getConfigurationHandler().get(CONFIG_LOGGING_LEVEL, Levels.INFO.name());
        return fromName(config);
    }

    private static Levels fromName(String name) {
        name = name.toUpperCase().trim();
        try {
            return Levels.valueOf(name.toUpperCase().trim());
        }
        catch (Exception e) {
            return Levels.ERROR;
        }
    }
}
