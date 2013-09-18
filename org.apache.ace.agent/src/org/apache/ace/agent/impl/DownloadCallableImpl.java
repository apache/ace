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

import static org.apache.ace.agent.impl.ConnectionUtil.closeSilently;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

import org.apache.ace.agent.DownloadHandle.DownloadProgressListener;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;

/**
 * Responsible for actually downloading content from a download handle.
 */
final class DownloadCallableImpl implements Callable<DownloadResult> {
    /**
     * Size of the buffer used while downloading the content stream.
     */
    private static final int READBUFFER_SIZE = 4096;

    private final DownloadHandleImpl m_handle;
    private final DownloadProgressListener m_listener;
    private final File m_target;

    DownloadCallableImpl(DownloadHandleImpl handle, DownloadProgressListener listener, File target) {
        m_handle = handle;
        m_listener = listener;
        m_target = target;
    }

    @Override
    public DownloadResult call() throws Exception {
        ContentRangeInputStream is = null;
        OutputStream os = null;

        long targetLength = m_target.length();
        boolean appendTarget = (targetLength > 0);

        try {
            is = new ContentRangeInputStream(m_handle.getConnectionHandler(), m_handle.getURL(), targetLength);
            os = new BufferedOutputStream(new FileOutputStream(m_target, appendTarget));

            byte buffer[] = new byte[READBUFFER_SIZE];
            long bytesRead = targetLength, totalBytes = -1L;
            int read;

            try {
                while (!Thread.currentThread().isInterrupted() && (read = is.read(buffer)) >= 0) {
                    os.write(buffer, 0, read);
                    // update local administration...
                    bytesRead += read;
                    totalBytes = is.getContentSize();

                    if (m_listener != null) {
                        m_listener.progress(bytesRead, totalBytes);
                    }
                }
            }
            finally {
                // Ensure that buffers are flushed in our output stream...
                os.flush();
            }

            boolean stoppedEarly = Thread.currentThread().isInterrupted() || (totalBytes > 0L && bytesRead < totalBytes);
            if (stoppedEarly) {
                m_handle.logDebug("Download stopped early: %d of %d bytes downloaded... (%d)", bytesRead, totalBytes, targetLength);

                return new DownloadResultImpl(DownloadState.STOPPED);
            }

            m_handle.logDebug("Download completed: %d bytes downloaded...", totalBytes);

            return new DownloadResultImpl(DownloadState.SUCCESSFUL, m_target);
        }
        finally {
            closeSilently(os);
            closeSilently(is);
        }
    }
}
