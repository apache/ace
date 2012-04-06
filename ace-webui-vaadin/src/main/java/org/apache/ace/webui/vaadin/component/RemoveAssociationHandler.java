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
package org.apache.ace.webui.vaadin.component;

import org.apache.ace.client.repository.RepositoryObject;

/**
 * Represents a handler for removing assocations for a repository object.
 */
public interface RemoveAssociationHandler<REPO_OBJECT extends RepositoryObject> {

    /**
     * Removes the left-hand side associations for a given repository object.
     * 
     * @param object the repository object to remove the left-hand side associations;
     * @param other the (left-hand side) repository object to remove the associations for.
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    void removeLeftSideAssociation(REPO_OBJECT object, RepositoryObject other);

    /**
     * Removes the right-hand side associations for a given repository object.
     * 
     * @param object the repository object to remove the right-hand side associations;
     * @param other the (right-hand side) repository object to remove the associations for.
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    void removeRightSideAssocation(REPO_OBJECT object, RepositoryObject other);
}