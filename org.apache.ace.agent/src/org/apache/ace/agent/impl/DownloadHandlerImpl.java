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
import java.net.URL;

import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandler;

public class DownloadHandlerImpl extends ComponentBase implements DownloadHandler {
    private final File m_dataLocation;

    public DownloadHandlerImpl(File dataLocation) {
        super("downloads");

        m_dataLocation = dataLocation;
    }

    /**
     * @return the location to (temporarily) store data.
     */
    public File getDataLocation() {
        return m_dataLocation;
    }

    @Override
    public DownloadHandle getHandle(URL url) {
        return new DownloadHandleImpl(this, url);
    }
}
