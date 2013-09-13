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
package org.apache.ace.agent;

import java.util.concurrent.TimeUnit;

/**
 * A {@link DownloadHandle} provides control over an asynchronous download and access to the resulting file when the it
 * is completed. <br/>
 * <br/>
 * Consumers must consider the following rules;
 * <ul>
 * <li>Control methods must be called in logical order.
 * <li>Implementations are not expected to be thread-safe.
 * <li>Resulting files must be assumed to be transient.
 * <ul>
 */
public interface DownloadHandle {
    /**
     * Callback interface; when registered the completed method will be invoked when the download terminates for any
     * reason.
     */
    interface DownloadProgressListener {
        /**
         * Called while downloading the content stream.
         * 
         * @param bytesRead
         *            The number of bytes that has been received so far;
         * @param totalBytes
         *            The total length of the content or -1 if unknown.
         */
        void progress(long bytesRead, long totalBytes);

        /**
         * Called when a download terminates.
         * 
         * @param result
         *            The result of the download.
         */
        void completed(DownloadResult result);
    }

    /**
     * Starts the download, reporting the result and progress to the supplied listeners.
     */
    void start(DownloadProgressListener listener);

    /**
     * Convenience method to start the download and block until it is finished.
     * 
     * @param timeout
     *            the timeout to wait for a result;
     * @param unit
     *            the unit of the timeout to wait for a result.
     * @return the download result, never <code>null</code>.
     */
    DownloadResult startAndAwaitResult(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Pauses the download.
     */
    void stop();

    /**
     * Releases any resources that may be held by the handle.
     */
    void discard();
}
