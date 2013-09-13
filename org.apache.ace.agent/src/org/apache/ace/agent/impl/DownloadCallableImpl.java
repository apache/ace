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

import static org.apache.ace.agent.impl.ConnectionUtil.close;
import static org.apache.ace.agent.impl.ConnectionUtil.closeSilently;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;

import org.apache.ace.agent.DownloadHandle.DownloadProgressListener;
import org.apache.ace.agent.DownloadState;

/**
 * Responsible for actually downloading content from a download handle.
 */
final class DownloadCallableImpl implements Callable<Void> {
    private static final int SC_OK = 200;
    private static final int SC_PARTIAL_CONTENT = 206;

    private final DownloadHandleImpl m_handle;
    private final DownloadProgressListener m_listener;
    private final File m_target;
    private final int m_readBufferSize;

    DownloadCallableImpl(DownloadHandleImpl handle, DownloadProgressListener listener, File target, int readBufferSize) {
        m_handle = handle;
        m_listener = listener;
        m_target = target;
        m_readBufferSize = readBufferSize;
    }

    @Override
    public Void call() throws Exception {
        int statusCode = 0;
        HttpURLConnection httpUrlConnection = null;

        try {
            boolean partialContent = false;
            boolean appendTarget = false;

            httpUrlConnection = m_handle.openConnection();

            long targetSize = m_target.length();
            if (targetSize > 0) {
                String rangeHeader = String.format("bytes=%d-", targetSize);

                m_handle.logDebug("Requesting Range %s", rangeHeader);

                httpUrlConnection.setRequestProperty("Range", rangeHeader);
            }

            statusCode = httpUrlConnection.getResponseCode();
            if (statusCode == SC_OK) {
                partialContent = false;
            }
            else if (statusCode == SC_PARTIAL_CONTENT) {
                partialContent = true;
            }
            else {
                // TODO handle retry-after?!
                throw new IOException("Unable to handle server response code " + statusCode);
            }

            long totalBytes = httpUrlConnection.getContentLength();
            if (partialContent) {
                String contentRange = httpUrlConnection.getHeaderField("Content-Range");
                if (contentRange == null) {
                    throw new IOException("Server returned no Content-Range for partial content");
                }
                if (!contentRange.startsWith("bytes ")) {
                    throw new IOException("Server returned non bytes Content-Range " + contentRange);
                }

                String tmp = contentRange;
                tmp = tmp.replace("byes ", "");
                String[] parts = tmp.split("/");
                String start = parts[0].split("-")[0];
                String end = parts[0].split("-")[1];

                m_handle.logDebug("Size: %d, range from %d to %d.", parts[1], start, end);

                if ("*".equals(parts[1])) {
                    totalBytes = -1;
                }
                else {
                    totalBytes = Long.parseLong(parts[1]);
                }
            }

            long bytesRead = 0l;
            if (partialContent) {
                bytesRead = targetSize;
                appendTarget = true;
            }

            InputStream inputStream = httpUrlConnection.getInputStream();
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(m_target, appendTarget));

            byte buffer[] = new byte[m_readBufferSize];
            int read;

            try {
                while (!Thread.currentThread().isInterrupted() && (read = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, read);
                    bytesRead += read;

                    m_listener.progress(bytesRead, totalBytes);
                }
            }
            finally {
                closeSilently(outputStream);
                closeSilently(inputStream);
            }

            boolean stoppedEarly = (totalBytes > 0L && bytesRead < totalBytes);
            if (stoppedEarly) {
                m_handle.logDebug("Download stopped early: %d of %d bytes downloaded...", bytesRead, totalBytes);

                m_listener.completed(new DownloadResultImpl(DownloadState.STOPPED, (File) null, statusCode));
            } else {
                m_handle.logDebug("Download completed: %d bytes downloaded...", totalBytes);
                
                m_listener.completed(new DownloadResultImpl(DownloadState.SUCCESSFUL, m_target, statusCode));
            }
        }
        catch (Exception e) {
            m_handle.logWarning("Download failed!", e);

            m_listener.completed(new DownloadResultImpl(DownloadState.FAILED, e, statusCode));
        }
        finally {
            close(httpUrlConnection);
        }

        return null;
    }
}
