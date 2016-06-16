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
package org.apache.ace.client.repository;

import java.io.IOException;

import org.osgi.annotation.versioning.ProviderType;

/**
 * An interface that can be implemented by anybody that wants to be invoked as part of the
 * pre-commit cycle of a RepositoryAdmin.
 */
@ProviderType
public interface PreCommitMember {
    /**
     * Resets any volatile changes that might have been made as part of earlier operations.
     */
    public void reset();

    /**
     * Checks if there are any changes that need to be applied as part of the pre-commit
     * cycle.
     */
    public boolean hasChanges();

    /**
     * Invokes the pre-commit cycle. You should do all the work here and throw an exception
     * if for some reason the work fails.
     * 
     * @throws IOException when the work fails and the commit should be aborted
     */
    public void preCommit() throws IOException;
}
