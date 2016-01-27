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

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;

import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.LoggingHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * Default thread-safe {@link LoggingHandler} implementation that logs messages to {@link System.out} .
 */
public class LoggingHandlerImpl extends ComponentBase implements LoggingHandler, EventListener {
    private static final Levels DEFAULT_LEVEL = Levels.WARNING;

    private final BundleContext m_context;

    private volatile Levels m_logLevel;

    public LoggingHandlerImpl(BundleContext context) {
        // Obtain the system/framework setting as early as possible to ensure that we start logging 
        // at the right level from the start (avoiding missing log statements)...
        this(context, fromName(context.getProperty(CONFIG_LOGGING_LEVEL), DEFAULT_LEVEL));
    }

    public LoggingHandlerImpl(BundleContext context, Levels defaultLevel) {
        super("logging");
        m_context = context;
        m_logLevel = defaultLevel;
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (EVENT_AGENT_CONFIG_CHANGED.equals(topic)) {
            String newValue = payload.get(CONFIG_LOGGING_LEVEL);

            m_logLevel = fromName(newValue, m_logLevel);
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

        if (!invokeExternalLogService(logLevel, message, exception)) {
            invokeInternalLogService(logLevel, component, message, exception);
        }
    }

    private boolean invokeInternalLogService(Levels logLevel, String component, String message, Throwable exception) {
        System.out.printf("[%s] %TT (%s) %s%n", logLevel, new Date(), component, message);
        if (exception != null) {
            exception.printStackTrace(System.out);
        }
        return true;
    }

    /**
     * Bridges events from out local event-handling methods to the first (external) LogService implementation. As we do
     * not have a dependency on the (external!) LogService API we cannot always call like we normally would do for
     * OSGi-services. Instead, we need to do some advanced reflection trickery in order to call the first found
     * LogService.
     * 
     * @return <code>true</code> if an external log service was successfully called, <code>false</code> otherwise.
     */
    private boolean invokeExternalLogService(Levels logLevel, String message, Throwable exception) {
        try {
            ServiceReference<?>[] refs = m_context.getAllServiceReferences(LogService.class.getName(), null);
            if (refs != null && refs.length > 0) {
                // if we've found one (or more) we pick the first match
                Object svc = m_context.getService(refs[0]);
                if (svc != null) {
                    try {
                        Method m = svc.getClass().getMethod("log", Integer.TYPE, String.class, Throwable.class);
                        m.setAccessible(true); // Not entirely sure why this is needed for a public method...
                        m.invoke(svc, logLevel.getLogLevel(), message, exception);
                        // Success!
                        return true;
                    }
                    finally {
                        // make sure we always unget our service reference
                        m_context.ungetService(refs[0]);
                    }
                }
            }
        }
        catch (Exception e) {
            // there is a lot that can go wrong, but not much we can do at this point
            // beyond logging the error message to our default logging implementation...
            invokeInternalLogService(Levels.ERROR, "logging", "Failed to invoke external LogService: " + e.getMessage(), e);
        }

        return false;
    }

    private static Levels fromName(String name, Levels defaultLevel) {
        try {
            if (name != null) {
                return Levels.valueOf(name.toUpperCase().trim());
            }
        }
        catch (Exception e) {
            // Fall through...
        }
        return defaultLevel;
    }
}
