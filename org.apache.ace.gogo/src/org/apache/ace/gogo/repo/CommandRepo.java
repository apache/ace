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
package org.apache.ace.gogo.repo;

import static org.apache.ace.gogo.repo.RepositoryUtil.findResources;
import static org.apache.ace.gogo.repo.RepositoryUtil.getRequirement;

import java.util.List;

import org.apache.ace.bnd.repository.AceObrRepository;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

import aQute.bnd.deployer.repository.FixedIndexedRepo;

public class CommandRepo {

    private final AceObrRepository m_repository;

    public CommandRepo(AceObrRepository repository) {
        m_repository = repository;
    }

    public FixedIndexedRepo repo() {
        return m_repository;
    }

    public void list() throws Exception {
        for (Resource resource : findResources(m_repository)) {
            System.out.println(resource.toString());
        }
    }

    public List<CommandResource> find() throws Exception {
        return find(null);
    }

    public List<CommandResource> find(String filter) throws Exception {
        Requirement requirement = getRequirement(filter);
        return CommandResource.wrap(this, findResources(m_repository, requirement));
    }
}
