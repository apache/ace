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

import java.util.Map;
import java.util.logging.Level;

import org.apache.ace.agent.ConfigurationHandler;

public class ConfigurationHandlerImpl implements ConfigurationHandler {

    private final AgentContext m_agentContext;

    public ConfigurationHandlerImpl(AgentContext agentContext) {
        m_agentContext = agentContext;
    }

    @Override
    public void setSyncInterval(long seconds) {
    }

    @Override
    public long getSyncInterval() {
        return 10;
    }

    @Override
    public void setSyncRetries(int value) {
    }

    @Override
    public int getSyncRetries() {
        return 3;
    }

    @Override
    public void setUpdateStreaming(boolean flag) {
    }

    @Override
    public boolean getUpdateStreaming() {
        return false;
    }

    @Override
    public void setStopUnaffected(boolean flag) {
    }

    @Override
    public boolean getStopUnaffected() {
        return false;
    }

    @Override
    public void setFixPackage(boolean flag) {
    }

    @Override
    public boolean getFixPackages() {
        return false;
    }

    @Override
    public void setLogLevel(Level level) {
    }

    @Override
    public Level getLogLevel() {
        return null;
    }

    @Override
    public void setMap(Map<String, String> configuration) {
    }

    @Override
    public Map<String, String> getMap() {
        return null;
    }

}
