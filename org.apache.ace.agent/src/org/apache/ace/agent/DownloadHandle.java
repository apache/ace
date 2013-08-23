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
     * Size of the buffer used while downloading the content stream.
     */
    int DEFAULT_READBUFFER_SIZE = 1024;

    /**
     * Callback interface; when registered the progress method will be invoked while downloading the content stream for
     * every {@link READBUFFER_SIZE} bytes.
     */
    interface ProgressListener {
        /**
         * Called while downloading the content stream.
         * 
         * @param contentLength The total length of the content or -1 if unknown.
         * @param progress The number of bytes that has been received so far.
         */
        void progress(long contentLength, long progress);
    }

    /**
     * Callback interface; when registered the completed method will be invoked when the download terminates for any
     * reason.
     * 
     */
    interface ResultListener {
        /**
         * Called when a download terminates.
         * 
         * @param result The result of the download.
         */
        void completed(DownloadResult result);
    }

    /**
     * Registers the progress listener.
     * 
     * @param listener The progress listener.
     * @return this
     */
    DownloadHandle setProgressListener(ProgressListener listener);

    /**
     * Registers the completion listener.
     * 
     * @param listener The completion listener.
     * @return this
     */
    DownloadHandle setCompletionListener(ResultListener listener);

    /**
     * Starts the download.
     * 
     * @return this
     */
    DownloadHandle start();

    /**
     * Pauses the download.
     * 
     * @return this
     */
    DownloadHandle stop();

    /**
     * Retrieves the download result. Will wait for completion before returning.
     * 
     * @return The result of the download
     */
    DownloadResult result();

    /**
     * Releases any resources that may be held by the handle.
     */
    void discard();
}
