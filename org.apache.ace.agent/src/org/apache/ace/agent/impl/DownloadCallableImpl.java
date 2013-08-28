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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

class DownloadCallableImpl implements Callable<Void> {

    // test support
    static final int FAIL_OPENCONNECTION = 1;
    static final int FAIL_OPENINPUTSTREAM = 2;
    static final int FAIL_OPENOUTPUTSTREAM = 3;
    static final int FAIL_AFTERFIRSTWRITE = 4;

    private final DownloadHandleImpl m_handle;
    private final URL m_source;
    private final File m_target;
    private final int m_readBufferSize;
    private final int m_failAtPosition;

    private volatile boolean m_abort = false;

    DownloadCallableImpl(DownloadHandleImpl handle, URL source, File target, int readBufferSize, int failAtPosition) {
        m_handle = handle;
        m_source = source;
        m_target = target;
        m_readBufferSize = readBufferSize;
        m_failAtPosition = failAtPosition;
    }

    @Override
    public Void call() throws Exception {
        return download();
    }

    /**
     * Abort the download. Used instead of a cancel on the future so normal completion can take place.
     */
    void abort() {
        m_abort = true;
    }

    @SuppressWarnings("resource")
    private Void download() {

        int statusCode = 0;
        Map<String, List<String>> headerFields = null;

        BufferedInputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        HttpURLConnection httpUrlConnection = null;
        try {

            boolean partialContent = false;
            boolean appendTarget = false;

            if (m_failAtPosition == FAIL_OPENCONNECTION)
                throw new IOException("Failed on openConnection on request");
            httpUrlConnection = (HttpURLConnection) m_source.openConnection();

            long targetSize = m_target.length();
            if (targetSize > 0) {
                String rangeHeader = "bytes=" + targetSize + "-";
                m_handle.logDebug("Requesting Range %s", rangeHeader);
                httpUrlConnection.setRequestProperty("Range", rangeHeader);
            }

            statusCode = httpUrlConnection.getResponseCode();
            headerFields = httpUrlConnection.getHeaderFields();
            if (statusCode == 200) {
                partialContent = false;
            }
            else if (statusCode == 206) {
                partialContent = true;
            }
            else {
                throw new IOException("Unable to handle server response code " + statusCode);
            }

            if (m_failAtPosition == FAIL_OPENINPUTSTREAM)
                throw new IOException("Failed on openConnection on request");
            inputStream = new BufferedInputStream(httpUrlConnection.getInputStream());

            long contentLength = httpUrlConnection.getContentLength();
            if (partialContent) {
                String contentRange = httpUrlConnection.getHeaderField("Content-Range");
                if (contentRange == null) {
                    throw new IOException("Server returned no Content-Range for partial content");
                }
                if (!contentRange.startsWith("bytes ")) {
                    throw new IOException("Server returned non byes Content-Range " + contentRange);
                }
                String tmp = contentRange;
                tmp = tmp.replace("byes ", "");
                String[] parts = tmp.split("/");
                String start = parts[0].split("-")[0];
                String end = parts[0].split("-")[1];
                System.out.println("size:" + parts[1]);
                System.out.println("from:" + start);
                System.out.println("too:" + end);

                if (parts[1].equals("*"))
                    contentLength = -1;
                else
                    contentLength = Long.parseLong(parts[1]);
            }

            long progress = 0l;
            if (partialContent) {
                progress = targetSize;
                appendTarget = true;
            }

            if (m_failAtPosition == FAIL_OPENOUTPUTSTREAM)
                throw new IOException("Failed on outputStream");
            outputStream = new BufferedOutputStream(new FileOutputStream(m_target, appendTarget));

            byte buffer[] = new byte[m_readBufferSize];
            int read = -1;
            while (!m_abort && (read = inputStream.read(buffer)) >= 0) {

                outputStream.write(buffer, 0, read);
                progress += read;
                m_handle.progressCallback(statusCode, headerFields, contentLength, progress);

                if (m_failAtPosition == FAIL_AFTERFIRSTWRITE)
                    throw new IOException("Failed after first write");

                if (Thread.currentThread().isInterrupted())
                    m_abort = true;
            }

            if (m_abort) {
                m_handle.logDebug("Download stopped: %s" + m_source.toExternalForm());
                m_handle.stoppedCallback(statusCode, headerFields, null);
            }
            else {
                m_handle.logDebug("Download completed: %s", m_source.toExternalForm());
                m_handle.successfulCallback(statusCode, headerFields);
            }
        }
        catch (Exception e) {
            m_handle.failedCallback(statusCode, headerFields, e);
        }
        cleanupQuietly(httpUrlConnection, inputStream, outputStream);
        return null;
    }

    private static void cleanupQuietly(HttpURLConnection httpUrlConnection, InputStream inputStream, OutputStream outputStream) {
        if (httpUrlConnection != null)
            httpUrlConnection.disconnect();
        if (inputStream != null)
            try {
                inputStream.close();
            }
            catch (IOException e) {
                // ignore
            }
        if (outputStream != null)
            try {
                outputStream.close();
            }
            catch (IOException e) {
                // ignore
            }
    }
}
