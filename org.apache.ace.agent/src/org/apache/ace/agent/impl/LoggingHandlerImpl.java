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

import static org.apache.ace.agent.AgentConstants.EVENT_AGENT_CONFIG_CHANGED;
import static org.apache.ace.agent.AgentConstants.CONFIG_LOGGING_LEVEL;

import java.util.Date;
import java.util.Map;

import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.LoggingHandler;

/**
 * Default thread-safe {@link LoggingHandler} implementation that logs messages to {@link System.out} .
 */
public class LoggingHandlerImpl extends ComponentBase implements LoggingHandler, EventListener {
    private static final Levels DEFAULT_LEVEL = Levels.WARNING;

    private volatile Levels m_logLevel;

    public LoggingHandlerImpl() {
        this(DEFAULT_LEVEL);
    }

    public LoggingHandlerImpl(Levels defaultLevel) {
        super("logging");
        m_logLevel = defaultLevel;
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            String newValue = payload.get(CONFIG_LOGGING_LEVEL);

            m_logLevel = fromName(newValue);
        }
    }

    @Override
    public void logDebug(String component, String message, Throwable exception, Object... args) {
        log(Levels.DEBUG, component, message, exception, args);
    }

    @Override
    public void logInfo(String component, String message, Throwable exception, Object... args) {
        log(Levels.INFO, component, message, exception, args);
    }

    @Override
    public void logWarning(String component, String message, Throwable exception, Object... args) {
        log(Levels.WARNING, component, message, exception, args);
    }

    @Override
    public void logError(String component, String message, Throwable exception, Object... args) {
        log(Levels.ERROR, component, message, exception, args);
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStop() throws Exception {
        getEventsHandler().removeListener(this);
    }

    private void log(Levels logLevel, String component, String message, Throwable exception, Object... args) {
        if (m_logLevel.ordinal() > logLevel.ordinal()) {
            // we're not interested at this log entry...
            return;
        }
        if (args.length > 0) {
            message = String.format(message, args);
        }
        System.out.printf("[%s] %TT (%s) %s%n", logLevel, new Date(), component, message);

        if (exception != null) {
            exception.printStackTrace(System.out);
        }
    }

    private static Levels fromName(String name) {
        if (name == null) {
            return DEFAULT_LEVEL;
        }
        try {
            return Levels.valueOf(name.toUpperCase().trim());
        }
        catch (Exception e) {
            return DEFAULT_LEVEL;
        }
    }
}
