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

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a download task.
 * 
 */
public interface DownloadResult {

    /**
     * Returns the state of the result.
     * 
     * @return The state
     */
    DownloadState getState();

    /**
     * Returns the download file.
     * 
     * @return The file, <code>null</code> if the download was unsuccessful
     */
    //TODO inputstream
    File getFile();

    int getCode();
    
    Map<String, List<String>> getHeaders();
    /**
     * Return the cause of an unsuccessful download.
     * 
     * @return The cause, <code>null</code> if the download was successful
     */
    Throwable getCause();
}
