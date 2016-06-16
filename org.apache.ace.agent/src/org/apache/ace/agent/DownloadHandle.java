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

import java.util.concurrent.Future;

import org.osgi.annotation.versioning.ConsumerType;

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
@ConsumerType
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
         */
        void progress(long bytesRead);
    }

    /**
     * Starts the download, reporting the result and progress to the supplied listeners.
     * 
     * @return a future promise for the download result, never <code>null</code>.
     */
    Future<DownloadResult> start(DownloadProgressListener listener);

    /**
     * Pauses the download.
     */
    void stop();

    /**
     * Releases any resources that may be held by the handle.
     */
    void discard();
}
