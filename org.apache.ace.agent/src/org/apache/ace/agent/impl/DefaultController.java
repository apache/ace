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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.AgentUpdateHandler;
import org.apache.ace.agent.ConfigurationHandler;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;

/**
 * Default configurable controller
 * 
 */
public class DefaultController implements Runnable {

    private final AgentContext m_agentContext;
    private volatile ScheduledFuture<?> m_future;

    public DefaultController(AgentContext agentContext) {
        m_agentContext = agentContext;
    }

    public void start() {
        schedule(1);
    }

    public void stop() {
        unSchedule();
    }

    public void run() {
        ConfigurationHandler configurationHandler = m_agentContext.getConfigurationHandler();
        long syncInterval = configurationHandler.getSyncInterval();
        try {
            runSafeAgent();
            runSafeUpdate();
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
        reschedule(syncInterval);
    }

    private void runSafeAgent() throws RetryAfterException, IOException {
        AgentUpdateHandler deploymentHandler = m_agentContext.getAgentUpdateHandler();
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

    private void runSafeUpdate() throws RetryAfterException, IOException {
        DeploymentHandler deploymentHandler = m_agentContext.getDeploymentHandler();
        Version current = deploymentHandler.getInstalledVersion();
        SortedSet<Version> available = deploymentHandler.getAvailableVersions();
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        System.out.println("runSafeUpdate: " + current + ", latest: " + highest);
        if (highest.compareTo(current) > 0) {
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
        List<String> channelNames = m_agentContext.getAgentControl().getFeedbackChannelNames();
        for (String channelName : channelNames) {
            FeedbackChannel channel = m_agentContext.getAgentControl().getFeedbackChannel(channelName);
            if (channel != null)
                channel.sendFeedback();
        }
    }

    private void schedule(long seconds) {
        m_agentContext.getLogService().log(LogService.LOG_INFO, "Scheduling initial poll in " + seconds + " seconds");
        m_future = m_agentContext.getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void reschedule(long seconds) {
        m_agentContext.getLogService().log(LogService.LOG_DEBUG, "Scheduling next poll in " + seconds + " seconds");
        m_future = m_agentContext.getExecutorService().schedule(this, seconds, TimeUnit.SECONDS);
    }

    private void unSchedule() {
        if (m_future != null)
            m_future.cancel(true);
    }
}
