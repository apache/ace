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
package org.apache.ace.useradmin.repository;

import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;

/**
 * Wrapper for {@link Group} that prevents changes to the group when the store is out of sync with the main repository
 */
public class RepositoryGroup extends RepositoryUser implements Group {

    public RepositoryGroup(Group group, AtomicLong version, RepoCurrentChecker repoCurrentChecker) {
        super(group, version, repoCurrentChecker);
    }

    @Override
    public boolean addMember(Role role) {
        checkRepoUpToDate();
        return ((Group) m_delegate).addMember(role);
    }

    @Override
    public boolean addRequiredMember(Role role) {
        checkRepoUpToDate();
        return ((Group) m_delegate).addMember(role);
    }

    @Override
    public boolean removeMember(Role role) {
        checkRepoUpToDate();
        return ((Group) m_delegate).removeMember(role);
    }

    @Override
    public Role[] getMembers() {
        return ((Group) m_delegate).getMembers();
    }

    @Override
    public Role[] getRequiredMembers() {
        return ((Group) m_delegate).getRequiredMembers();
    }

}
