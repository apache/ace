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

import java.net.URL;
import java.util.concurrent.ExecutorService;

import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandler;
import org.osgi.service.log.LogService;

public class DownloadHandlerImpl extends HandlerBase implements DownloadHandler {

    @Override
    public DownloadHandle getHandle(URL url) {
        return new DownloadHandleImpl(this, url);
    }

    @Override
    public DownloadHandle getHandle(URL url, int readBufferSize) {
        return new DownloadHandleImpl(this, url, readBufferSize);
    }

    /*
     * handle support methods
     */
    ExecutorService getExecutor() {
        return getAgentContext().getExecutorService();
    }

    void logDebug(String message, Object... args) {
        getAgentContext().getLogService().log(LogService.LOG_DEBUG, message);
    }

    void logInfo(String message, Object... args) {
        getAgentContext().getLogService().log(LogService.LOG_INFO, message);
    }

    void logWarning(String message, Object... args) {
        getAgentContext().getLogService().log(LogService.LOG_WARNING, message);
    }
}
