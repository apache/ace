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

import static org.apache.ace.gogo.repo.RepositoryUtil.copyResource;
import static org.apache.ace.gogo.repo.RepositoryUtil.createRepository;
import static org.apache.ace.gogo.repo.RepositoryUtil.deleteResource;
import static org.apache.ace.gogo.repo.RepositoryUtil.findResources;
import static org.apache.ace.gogo.repo.RepositoryUtil.getIdentityVersionRequirement;
import static org.apache.ace.gogo.repo.RepositoryUtil.getRequirement;
import static org.apache.ace.gogo.repo.RepositoryUtil.getUrl;
import static org.apache.ace.gogo.repo.RepositoryUtil.indexDirectory;
import static org.apache.ace.gogo.repo.RepositoryUtil.uploadResource;

import java.net.URL;
import java.util.List;

import org.apache.ace.bnd.registry.RegistryImpl;
import org.apache.ace.bnd.repository.AceObrRepository;
import org.apache.ace.bnd.repository.AceUrlConnector;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.felix.service.command.Descriptor;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

import aQute.bnd.deployer.repository.FixedIndexedRepo;

public class RepoCommands {

    public final static String SCOPE = "repo";
    public final static String[] FUNCTIONS = new String[] { "repo", "index", "ls", "cp", "rm", "cd", "d" };

    private volatile ConnectionFactory m_connectionFactory;

    @Descriptor("Defines a repository")
    public CommandRepo repo(@Descriptor("the type e { R5, OBR }") String type, @Descriptor("url of the repository index") String location) throws Exception {
        AceObrRepository repo = createRepository(type, location);

        // ACE-624 allow support for different auth mechanisms...
        RegistryImpl registry = new RegistryImpl(m_connectionFactory, new AceUrlConnector(m_connectionFactory));
        repo.setRegistry(registry);

        return new CommandRepo(repo);
    }

    @Descriptor("Indexes a directory")
    public static URL index(@Descriptor("path to the directory") String directory) throws Exception {
        return indexDirectory(directory);
    }

    @Descriptor("lists resources in a repository")
    public static void ls(CommandRepo fromRepo) throws Exception {
        ls(fromRepo, null);
    }

    @Descriptor("lists resources in a repository")
    public static void ls(CommandRepo repo, String filter) throws Exception {

        FixedIndexedRepo sourceRepo = repo.repo();
        sourceRepo.reset();

        Requirement requirement = getRequirement(filter);
        List<Resource> resources = findResources(sourceRepo, requirement);

        for (Resource resource : resources) {
            String location = getUrl(resources.get(0));
            System.out.println(resource + " => " + location);
        }
    }

    @Descriptor("copy resources from one repository to another")
    public static void cp(CommandRepo fromRepo, CommandRepo toRepo) throws Exception {
        cp(fromRepo, toRepo, null);
    }

    @Descriptor("copy resources from one repository to another")
    public static void cp(CommandRepo fromRepo, CommandRepo toRepo, String filter) throws Exception {

        FixedIndexedRepo sourceRepo = fromRepo.repo();
        FixedIndexedRepo targetRepo = toRepo.repo();
        sourceRepo.reset();
        targetRepo.reset();

        Requirement requirement = getRequirement(filter);
        List<Resource> resources = findResources(sourceRepo, requirement);

        for (Resource resource : resources) {
            List<Resource> existingResources = findResources(targetRepo, getIdentityVersionRequirement(resource));
            if (existingResources.size() == 0) {
                Resource copied = copyResource(sourceRepo, targetRepo, resource);
                System.out.println("copied: " + copied);
            }
            else {
                System.out.println("skipped: " + existingResources.get(0));
            }
        }
    }

    @Descriptor("remove resources from a repository")
    public static void rm(CommandRepo fromRepo) throws Exception {
        rm(fromRepo, null);
    }

    @Descriptor("remove resources from a repository")
    public static void rm(CommandRepo fromRepo, String filter) throws Exception {

        FixedIndexedRepo repo = fromRepo.repo();
        repo.reset();

        Requirement requirement = getRequirement(filter);
        List<Resource> resources = findResources(repo, requirement);

        for (Resource resource : resources) {
            deleteResource(resource);
        }
    }

    @Descriptor("execute a continuous deployment cycle")
    public static List<CommandResource> cd(CommandRepo releaseRepo, CommandRepo sourceRepo, CommandRepo targetRepo) throws Exception {

        FixedIndexedRepo relRepo = releaseRepo.repo();
        FixedIndexedRepo srcRepo = sourceRepo.repo();
        FixedIndexedRepo tgtRepo = targetRepo.repo();
        relRepo.reset();
        srcRepo.reset();
        tgtRepo.reset();

        ContinuousDeployer cd = new ContinuousDeployer(tgtRepo, srcRepo, relRepo);
        try {
            List<Resource> deployedResources = cd.deployResources();
            return CommandResource.wrap(targetRepo, deployedResources);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Descriptor("deploy a resource to a repository")
    public static void d(CommandRepo repo, String url) throws Exception {
        d(repo, url, null);
    }

    @Descriptor("deploy a resource to a repository")
    public static void d(CommandRepo repo, String url, String filename) throws Exception {
        FixedIndexedRepo toRepo = repo.repo();
        URL location = new URL(url);
        uploadResource(toRepo, location, filename);
        toRepo.reset();
    }
}
