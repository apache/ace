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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;

/**
 * A {@link DownloadHandle} implementation that supports pause/resume semantics based on HTTP Range headers assuming the
 * server supports this feature.
 */
class DownloadHandleImpl implements DownloadHandle {
    /**
     * Size of the buffer used while downloading the content stream.
     */
    private static final int DEFAULT_READBUFFER_SIZE = 1024;

    private final DownloadHandlerImpl m_handler;
    private final URL m_url;
    private final int m_readBufferSize;

    private volatile Future<Void> m_future;
    private volatile File m_file;

    DownloadHandleImpl(DownloadHandlerImpl handler, URL url) {
        this(handler, url, DEFAULT_READBUFFER_SIZE);
    }

    DownloadHandleImpl(DownloadHandlerImpl handler, URL url, int readBufferSize) {
        m_handler = handler;
        m_url = url;
        m_readBufferSize = readBufferSize;
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
    public void start(DownloadProgressListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null!");
        }

        if (m_future != null && !m_future.isDone()) {
            throw new IllegalStateException("Can not call start on a handle that is already started!");
        }

        if (m_file == null) {
            try {
                m_file = File.createTempFile("download", ".bin", m_handler.getDataLocation());
            }
            catch (IOException e) {
                listener.completed(new DownloadResultImpl(DownloadState.FAILED, e, -1));
            }
        }

        m_future = getExecutor().submit(new DownloadCallableImpl(this, listener, m_file, m_readBufferSize));
    }

    @Override
    public DownloadResult startAndAwaitResult(long timeout, TimeUnit unit) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<DownloadResult> result = new AtomicReference<DownloadResult>();

        start(new DownloadProgressListener() {
            @Override
            public void progress(long bytesRead, long totalBytes) {
                // Nop
            }

            @Override
            public void completed(DownloadResult downloadResult) {
                result.set(downloadResult);
                latch.countDown();
            }
        });
        if (!latch.await(timeout, unit)) {
            throw new InterruptedException("Failed to obtain result within given time constaints!");
        }
        return result.get();
    }

    @Override
    public void stop() {
        Future<Void> future = m_future;
        if (future != null) {
            if (future.isDone()) {
                throw new IllegalStateException("Can not call stop on a handle that is not yet started or completed!");
            }

            future.cancel(true /* mayInterruptIfRunning */);
        }
        m_future = null;
    }

    final void logDebug(String message, Object... args) {
        m_handler.logDebug(message, args);
    }

    final void logWarning(String message, Throwable cause, Object... args) {
        m_handler.logWarning(message, cause, args);
    }

    final HttpURLConnection openConnection() throws IOException {
        return (HttpURLConnection) getConnectionHandler().getConnection(m_url);
    }

    private ConnectionHandler getConnectionHandler() {
        return m_handler.getConnectionHandler();
    }

    private ExecutorService getExecutor() {
        return m_handler.getExecutor();
    }
}
