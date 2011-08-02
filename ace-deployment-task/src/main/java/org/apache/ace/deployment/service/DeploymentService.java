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
package org.apache.ace.deployment.service;

import java.io.IOException;
import java.util.SortedSet;

import org.osgi.framework.Version;

/**
 * Deployment service can be used to talk to the management agent about deployment packages,
 * versions and updates, and to actually perform them. This interface co-exists with the
 * tasks that are also published by the management agent and that are probably more convenient
 * if you just want to schedule (checks for) updates.
 */
public interface DeploymentService {
    public Version getLocalVersion();
    public SortedSet<Version> getRemoteVersions() throws IOException;
    public void update(Version toVersion) throws Exception;
    
}
