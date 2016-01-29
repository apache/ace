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

import java.util.LinkedList;
import java.util.List;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class CommandResource {

    public static List<CommandResource> wrap(CommandRepo repo, List<Resource> resources) {
        List<CommandResource> commandResources = new LinkedList<>();
        for (Resource resource : resources) {
            commandResources.add(new CommandResource(repo, resource));
        }
        return commandResources;
    }

    public static CommandResource wrap(CommandRepo repo, Resource resource) {
        return new CommandResource(repo, resource);
    }

    private final CommandRepo m_repo;
    private final Resource m_resource;

    public CommandResource(CommandRepo repo, Resource resource) {
        m_repo = repo;
        m_resource = resource;
    }

    public CommandRepo getRepo() {
        return m_repo;
    }

    public Resource getResource() {
        return m_resource;
    }

    public String getIdentity() {
        return RepositoryUtil.getIdentity(m_resource);
    }

    public String getVersion() {
        return RepositoryUtil.getVersion(m_resource).toString();
    }

    public String getUrl() {
        return RepositoryUtil.getUrl(m_resource);
    }

    public String getMimetype() {
        return RepositoryUtil.getMimetype(m_resource);
    }

    public List<Capability> getCapabilities(String namespace) {
        return m_resource.getCapabilities(namespace);
    }

    public List<Requirement> getRequirements(String namespace) {
        return m_resource.getRequirements(namespace);
    }
}
