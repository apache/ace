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
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;

/**
 * A {@link DownloadHandle} implementation that supports pause/resume semantics based on HTTP Range headers assuming the
 * server supports this feature.
 */
class DownloadHandleImpl implements DownloadHandle {

    private final DownloadHandlerImpl m_handler;
    private final URL m_url;
    private final int m_readBufferSize;

    private volatile boolean m_started = false;
    private volatile boolean m_completed = false;

    private volatile Future<Void> m_future;
    private volatile DownloadCallableImpl m_callable;
    private volatile File m_file;

    private volatile ProgressListener m_progressListener;
    private volatile ResultListener m_completionListener;

    private volatile DownloadResult m_downloadResult;

    DownloadHandleImpl(DownloadHandlerImpl handler, URL url) {
        this(handler, url, DEFAULT_READBUFFER_SIZE);
    }

    DownloadHandleImpl(DownloadHandlerImpl handler, URL url, int readBufferSize) {
        m_handler = handler;
        m_url = url;
        m_readBufferSize = readBufferSize;
    }

    @Override
    public DownloadHandle setProgressListener(ProgressListener listener) {
        m_progressListener = listener;
        return this;
    }

    @Override
    public DownloadHandle setCompletionListener(ResultListener listener) {
        m_completionListener = listener;
        return this;
    }

    @Override
    public DownloadHandle start() {
        return start(-1);
    }

    DownloadHandle start(int failAtPosition) {
        if (m_started) {
            throw new IllegalStateException("Can not call start on a handle that is already started");
        }
        if (m_file == null) {
            try {
                m_file = File.createTempFile("download", ".bin");
            }
            catch (IOException e) {
                failedCallback(0, null, e);
            }
        }
        startDownload(failAtPosition);
        return this;
    }

    @Override
    public DownloadHandle stop() {
        if (!m_started && !m_completed) {
            throw new IllegalStateException("Can not call stop on a handle that is not yet started");
        }
        m_started = false;
        stopDownload();
        return this;
    }

    @Override
    public DownloadResult result() {
        if (m_completed)
            return m_downloadResult;
        if (!m_started)
            throw new IllegalStateException("Can not call result on a handle that is not yet started");
        try {
            m_future.get();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return m_downloadResult;
    }

    @Override
    public void discard() {
        if (m_started)
            stop();
        m_file.delete();
    }

    void progressCallback(int statusCode, Map<String, List<String>> headers, long contentLength, long progress) {
        callProgressListener(m_progressListener, contentLength, progress);
    }

    void successfulCallback(int statusCode, Map<String, List<String>> headers) {
        m_started = false;
        m_completed = true;
        m_downloadResult = new DownloadResultImpl(DownloadState.SUCCESSFUL, m_file, statusCode, headers, null);
        callCompletionListener(m_completionListener, m_downloadResult);
    }

    void stoppedCallback(int statusCode, Map<String, List<String>> headers, Throwable cause) {
        m_started = false;
        m_completed = false;
        m_downloadResult = new DownloadResultImpl(DownloadState.STOPPED, null, statusCode, headers, cause);
        callCompletionListener(m_completionListener, m_downloadResult);
    }

    void failedCallback(int statusCode, Map<String, List<String>> headers, Throwable cause) {
        m_started = false;
        m_completed = false;
        m_downloadResult = new DownloadResultImpl(DownloadState.FAILED, null, statusCode, headers, cause);
        callCompletionListener(m_completionListener, m_downloadResult);
    }

    void logDebug(String message, Object... args) {
        m_handler.logDebug(message, args);
    }

    void logInfo(String message, Object... args) {
        m_handler.logInfo(message, args);
    }

    void logWarning(String message, Object... args) {
        m_handler.logWarning(message, args);
    }

    private void startDownload(int failAtPosition) {
        m_started = true;
        m_callable = new DownloadCallableImpl(this, m_url, m_file, m_readBufferSize, failAtPosition);
        m_future = m_handler.getExecutor().submit(m_callable);
    }

    private void stopDownload() {
        m_started = false;
        m_callable.abort();
    }

    private static void callProgressListener(ProgressListener listener, long contentLength, long progress) {
        if (listener != null) {
            try {
                listener.progress(contentLength, progress);
            }
            catch (Exception e) {
                // ignore
            }
        }
    }

    private static void callCompletionListener(ResultListener listener, DownloadResult result) {
        if (listener != null && result != null) {
            try {
                listener.completed(result);
            }
            catch (Exception e) {
                // ignore
            }
        }
    }
}
