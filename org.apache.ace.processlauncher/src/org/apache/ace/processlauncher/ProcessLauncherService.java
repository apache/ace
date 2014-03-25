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
package org.apache.ace.processlauncher;

import java.io.IOException;

import aQute.bnd.annotation.ProviderType;

/**
 * Provides a managed service factory for launching processes based on a certain launch
 * configuration.
 */
@ProviderType
public interface ProcessLauncherService {

    /** The service PID that is used for registration of this service factory. */
    String PID = "org.apache.ace.processlauncher";

    /**
     * Returns the number of launch configurations currently available.
     * 
     * @return the number of launch configurations, >= 0.
     */
    int getLaunchConfigurationCount();

    /**
     * Returns the number of running processes.
     * 
     * @return a running process count, >= 0.
     * @throws IOException in case of I/O problems determining the number of running processes.
     */
    int getRunningProcessCount() throws IOException;

}
