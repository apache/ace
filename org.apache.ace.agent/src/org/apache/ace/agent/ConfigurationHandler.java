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

import java.util.Map;
import java.util.logging.Level;

/**
 * The agent's persisted configuration. External launchers can override the default values through system properties
 * when the agent starts. However, once a configuration value has been stored in the persisted configuration is will no
 * longer be overwritten by system properties. This ensures a simple system restart will not override configuration set
 * by runtime controllers.
 */
// TODO should we support recovery by allow config overrides through system properties or should a luancher just wip
// the bundle cache when it requires a clean boostrap?
public interface ConfigurationHandler {

    // NOTE Configuration of the default subsystems for identification, discovery and connection handling is not part of
    // this definition. Even though they are default implementations the are still extensions.

    /**
     * Sync interval; When sync is active it will automatically install updates and send feedback to the server. The
     * time unit is seconds. A value of 0 or less disables the sync.
     */
    String CONFIG_KEY_SYNC_INTERVAL = ConfigurationHandler.class.getPackage().getName() + ".syncinterval";
    long CONFIG_DEFAULT_SYNC_INTERVAL = 300l;

    void setSyncInterval(long seconds);

    long getSyncInterval();

    /**
     * SyncRetries value; When an install fails during a sync the agent can try to recover by ignoring optimization
     * flags and potentially restarting a Deployment Package download. A value of 1 or less disables the retry behavior.
     */
    String CONFIG_KEY_SYNC_RETRIES = ConfigurationHandler.class.getPackage().getName() + ".syncretries";
    int CONFIG_DEFAULT_SYNC_RETRIES = 3;

    void setSyncRetries(int value);

    int getSyncRetries();

    /**
     * UpdateStreaming flag; When set Deployment Packages are installed directly from the download stream reducing
     * overhead and disk usage, but disabling resume capabilities. This strategy is of interest to highly resource
     * constraint devices and/or system with highly reliable connectivity and no need for resume semantics.
     */
    String CONFIG_KEY_STREAMING_UPDATES = ConfigurationHandler.class.getPackage().getName() + ".updatestreaming";
    boolean CONFIG_DEFAULT_UPDATE_STREAMING = false;

    void setUpdateStreaming(boolean flag);

    boolean getUpdateStreaming();

    /**
     * StopUnaffected flag; When set all target bundles of a Deployment Package will be restarted as part of the
     * deployment session. Otherwise the agent tries to minimize the impact by only restarting bundles that are actually
     * affected. Not stopping unaffected bundles reduces overhead, but may fail in complex wiring scenarios.
     */
    String CONFIG_KEY_STOP_UNAFFECTED = ConfigurationHandler.class.getPackage().getName() + ".stopunaffected";
    boolean CONFIG_DEFAULT_STOP_UNAFFECTED = true; // spec behavior

    void setStopUnaffected(boolean flag);

    boolean getStopUnaffected();

    /**
     * FixPackages flag; When set the Agent will request the server for fix packages instead of full deployment
     * packages. This behavior significantly reduces bandwidth consumption.
     */
    String CONFIG_KEY_FIX_PACKAGES = ConfigurationHandler.class.getPackage().getName() + ".fixpackages";
    boolean CONFIG_DEFAULT_FIX_PACKAGES = true;

    void setFixPackage(boolean flag);

    boolean getFixPackages();

    /**
     * Log level; Logging uses standard Java logging to guarantee optimal compatibility in any standard Java
     * environment.
     */
    String CONFIG_KEY_LOG_LEVEL = ConfigurationHandler.class.getPackage().getName() + ".loglevel";
    Level CONFIG_DEFAULT_LOG_LEVEL = Level.INFO;

    void setLogLevel(Level level);

    Level getLogLevel();

    /**
     * Custom configuration keys; This is provided to allow a launcher to specify system properties that should be
     * included in the persistent configuration map. The initial values of these properties are only read once when the
     * agent starts and only stored if no value is set in the configuration map.
     */
    String CONFIG_KEY_EXTENSION_PROPERTIES = ConfigurationHandler.class.getPackage().getName() + ".extensionkeys";

    /**
     * Direct access to the configuration map; This is provided to allow extensions to access custom configuration
     * properties.
     */
    void setMap(Map<String, String> configuration);

    Map<String, String> getMap();
}
