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

import java.io.File;
import java.util.Dictionary;
import java.util.Formatter;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Internal interface that provides access to handlers, supporting services and static configuration.
 * 
 */
public interface AgentContext {

    /**
     * Return the identification handler.
     * 
     * @return The handler
     */
    IdentificationHandler getIdentificationHandler();

    /**
     * Return the discovery handler.
     * 
     * @return The handler
     */
    DiscoveryHandler getDiscoveryHandler();

    /**
     * Return the connection handler.
     * 
     * @return The handler
     */
    ConnectionHandler getConnectionHandler();

    /**
     * Return the deployment handler.
     * 
     * @return The handler
     */
    DeploymentHandler getDeploymentHandler();

    /**
     * Return the download handler.
     * 
     * @return The handler
     */
    DownloadHandler getDownloadHandler();

    /**
     * Return the configuration handler.
     * 
     * @return The handler
     */
    ConfigurationHandler getConfigurationHandler();

    /**
     * Return the update handler.
     * 
     * @return The handler
     */
    AgentUpdateHandler getAgentUpdateHandler();

    /**
     * Return the feedback handler.
     * 
     * @return The handler
     */
    FeedbackHandler getFeedbackHandler();

    /**
     * Return the executor service.
     * 
     * @return The service
     */
    ScheduledExecutorService getExecutorService();

    /**
     * Return the work directory.
     * 
     * @return The directory
     */
    File getWorkDir();

    /**
     * Post an event to any eventAdmin services outside the agent available at this time. There is no guarantee on
     * delivery. Only string values are supported to avoid any potential class-loading issues.
     * 
     * @param topic The topic
     * @param properties The payload
     */
    void postEvent(String topic, Dictionary<String, String> payload);

    /**
     * Log a debug message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param args The optional formatter arguments
     */
    void logDebug(String component, String message, Object... args);

    /**
     * Log a debug message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logDebug(String component, String message, Throwable cause, Object... args);

    /**
     * Log an info message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param args The optional formatter arguments
     */
    void logInfo(String component, String message, Object... args);

    /**
     * Log an info message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logInfo(String component, String message, Throwable cause, Object... args);

    /**
     * Log a warning message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param args The optional formatter arguments
     */
    void logWarning(String component, String message, Object... args);

    /**
     * Log a warning message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logWarning(String component, String message, Throwable cause, Object... args);

    /**
     * Log an error message. If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param args The optional formatter arguments
     */
    void logError(String component, String message, Object... args);

    /**
     * Log an error message.If <code>args</code> are provided the message will be processed as a format using the
     * standard {@link Formatter}.
     * 
     * @param component The component identifier, not <code>null</code>
     * @param message The log message or format, not <code>null</code>
     * @param cause The cause, may be <code>null</code>
     * @param args The optional formatter arguments
     */
    void logError(String component, String message, Throwable cause, Object... args);
}
