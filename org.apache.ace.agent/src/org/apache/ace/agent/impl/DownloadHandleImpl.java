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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;

/**
 * A {@link DownloadHandle} implementation that supports pause/resume semantics based on HTTP Range headers assuming the
 * server supports this feature.
 */
class DownloadHandleImpl implements DownloadHandle {
    private final DownloadHandlerImpl m_handler;
    private final File m_file;
    private final URL m_url;

    private volatile Future<DownloadResult> m_future;

    DownloadHandleImpl(DownloadHandlerImpl handler, URL url) {
        m_handler = handler;
        m_url = url;

        m_file = new File(m_handler.getDataLocation(), getDownloadFileName());

        m_handler.logDebug("Created download handle for %s in %s.", m_file.getName(), m_file.getPath());
    }

    @Override
    public void discard() {
        try {
            stop();
        }
        finally {
            m_file.delete();
        }
    }

    @Override
    public Future<DownloadResult> start(DownloadProgressListener listener) {
        if (m_future != null && !m_future.isDone()) {
            throw new IllegalStateException("Can not call start on a handle that is already started!");
        }
        m_future = null;

        ExecutorService executor = getExecutor();
        if (executor.isShutdown()) {
            m_handler.logWarning("Cannot start download, executor is shut down!");
        }
        else {
            m_future = executor.submit(new DownloadCallableImpl(this, listener, m_file));
        }

        return m_future;
    }

    @Override
    public void stop() {
        Future<?> future = m_future;
        m_future = null;
        if (future != null && !future.isDone()) {
            future.cancel(true /* mayInterruptIfRunning */);
        }
    }

    final ConnectionHandler getConnectionHandler() {
        return m_handler.getConnectionHandler();
    }

    final File getDownloadFile() {
        return m_file;
    }

    final URL getURL() {
        return m_url;
    }

    final void logDebug(String message, Object... args) {
        m_handler.logDebug(message, args);
    }

    final void logWarning(String message, Throwable cause, Object... args) {
        m_handler.logWarning(message, cause, args);
    }

    /**
     * @return the filename for the (temporary) download location.
     */
    private String getDownloadFileName() {
        try {
            return URLEncoder.encode(m_url.toExternalForm(), "ASCII");
        }
        catch (UnsupportedEncodingException exception) {
            throw new RuntimeException("ASCII encoding not supported?!");
        }
    }

    private ExecutorService getExecutor() {
        return m_handler.getExecutorService();
    }
}
