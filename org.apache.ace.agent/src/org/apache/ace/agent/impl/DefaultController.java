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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;

/**
 * Default configurable controller
 * 
 */
public class DefaultController implements Runnable {

    private final AgentControl m_agentControl;
    private final ScheduledExecutorService m_executorService;
    private volatile ScheduledFuture<?> m_future;

    public DefaultController(AgentControl agentControl, ScheduledExecutorService executorService) {
        m_agentControl = agentControl;
        m_executorService = executorService;
    }

    public void start() {
        reSchedule(getSyncInterval());
    }

    public void stop() {
        unSchedule();
    }

    public void run() {

        long syncInterval = getSyncInterval();
        try {
            runSafeAgent();
            // runSafeUpdate();
            runSafeFeedback();
        }
        catch (RetryAfterException e) {
            syncInterval = e.getSeconds();
        }
        catch (IOException e) {
            // TODO what to do
            e.printStackTrace();
        }
        catch (Exception e) {
            // TODO what to do
            e.printStackTrace();
        }
        reSchedule(syncInterval);
    }

    private void runSafeUpdate() throws RetryAfterException, IOException {

        DeploymentHandler deploymentHandler = getDeploymentHandler();

        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }

        if (highest.compareTo(current) > 1) {
            InputStream inputStream = deploymentHandler.getInputStream(highest, true);
            try {
                deploymentHandler.deployPackage(inputStream);
            }
            finally {
                inputStream.close();
            }
        }
    }

    private void runSafeFeedback() throws RetryAfterException, IOException {
        List<String> channelNames = m_agentControl.getFeedbackChannelNames();
        for (String channelName : channelNames) {
            FeedbackChannel channel = m_agentControl.getFeedbackChannel(channelName);
            if (channel != null)
                channel.sendFeedback();
        }
    }

    private void runSafeAgent() throws RetryAfterException, IOException {

        AgentUpdateHandler deploymentHandler = getAgentUpdateHandler();

        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }

        System.out.println("runSafeAgent: " + current + ", latest: " + highest);
        int val = highest.compareTo(current);
        if (val > 0) {
            InputStream inputStream = deploymentHandler.getInputStream(highest);
            deploymentHandler.install(inputStream);
        }
    }

    private void reSchedule(long seconds) {
        m_future = m_executorService.schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void unSchedule() {
        if (m_future != null)
            m_future.cancel(true);
    }

    private long getSyncInterval() {
        return getConfiguration().getSyncInterval();
    }

    private DeploymentHandler getDeploymentHandler() {
        return m_agentControl.getDeploymentHandler();
    }

    private AgentUpdateHandler getAgentUpdateHandler() {
        return m_agentControl.getAgentUpdateHandler();
    }

    private ConfigurationHandler getConfiguration() {
        return m_agentControl.getConfiguration();
    }
}
