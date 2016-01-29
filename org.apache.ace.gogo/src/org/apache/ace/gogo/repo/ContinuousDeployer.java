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

import static org.apache.ace.gogo.repo.DeployerUtil.filesDiffer;
import static org.apache.ace.gogo.repo.DeployerUtil.getBundleWithNewVersion;
import static org.apache.ace.gogo.repo.DeployerUtil.getNextSnapshotVersion;
import static org.apache.ace.gogo.repo.DeployerUtil.isSameBaseVersion;
import static org.apache.ace.gogo.repo.DeployerUtil.isSnapshotVersion;
import static org.apache.ace.gogo.repo.DeployerUtil.jarsDiffer;
import static org.apache.ace.gogo.repo.RepositoryUtil.copyResources;
import static org.apache.ace.gogo.repo.RepositoryUtil.findResources;
import static org.apache.ace.gogo.repo.RepositoryUtil.getFileName;
import static org.apache.ace.gogo.repo.RepositoryUtil.getIdentity;
import static org.apache.ace.gogo.repo.RepositoryUtil.getIdentityVersionRequirement;
import static org.apache.ace.gogo.repo.RepositoryUtil.getMimetype;
import static org.apache.ace.gogo.repo.RepositoryUtil.getString;
import static org.apache.ace.gogo.repo.RepositoryUtil.getType;
import static org.apache.ace.gogo.repo.RepositoryUtil.getVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.ace.bnd.repository.AceObrRepository;
import org.osgi.framework.Version;
import org.osgi.resource.Resource;

import aQute.bnd.deployer.repository.FixedIndexedRepo;
import aQute.bnd.service.Strategy;

public class ContinuousDeployer {
    final FixedIndexedRepo m_deploymentRepo;
    final FixedIndexedRepo m_developmentRepo;
    final FixedIndexedRepo m_releaseRepo;

    public ContinuousDeployer(FixedIndexedRepo deploymentRepo, FixedIndexedRepo developmentRepo, FixedIndexedRepo releaseRepo) {
        m_deploymentRepo = deploymentRepo;
        m_developmentRepo = developmentRepo;
        m_releaseRepo = releaseRepo;
    }

    /**
     * Deploys all resources from the development repository into the deployment repository.
     * 
     * @return a list of deployed resources
     * @throws Exception
     */
    public List<Resource> deployResources() throws Exception {
        List<Resource> developmentResources = findResources(m_developmentRepo, "*", "*");
        List<Resource> deployedResources = new ArrayList<>();
        for (Resource developmentResource : developmentResources) {
            deployedResources.add(deployResource(developmentResource));
        }
        return deployedResources;
    }

    /**
     * Deploys a resource to the deployment repository.
     * 
     * @param developmentResource
     *            The resource
     * @throws Exception
     *             On failure
     */
    private Resource deployResource(Resource developmentResource) throws Exception {
        List<Resource> releaseResources = findResources(m_releaseRepo, getIdentityVersionRequirement(developmentResource));
        if (releaseResources.size() > 0) {
            return deployReleasedResource(releaseResources.get(0));
        }
        else {
            return deploySnapshotResource(developmentResource);
        }
    }

    /**
     * Deploys a released resource from the release repository to the deployment repository if it has not been deployed
     * yet.
     * 
     * @param releasedResource
     *            The released resource
     * @return The deployed resource
     * @throws Exception
     */
    private Resource deployReleasedResource(Resource releasedResource) throws Exception {
        List<Resource> deployedResources = findResources(m_deploymentRepo, getIdentityVersionRequirement(releasedResource));
        if (deployedResources.size() == 0) {
            System.out.println("Uploading released resource: " + getString(releasedResource));
            List<Resource> copied = copyResources(m_releaseRepo, m_deploymentRepo, getIdentityVersionRequirement(releasedResource));
            if (copied.size() != 1) {
                throw new IllegalStateException("Expected one result after copy: " + getString(releasedResource));
            }
            return copied.get(0);
        }
        else {
            System.out.println("Released resource already deployed: " + getString(releasedResource));
            return deployedResources.get(0);
        }
    }

    /**
     * Deploys a snapshot resource to the deployment repository if it differs from the highest existing snapshot
     * resource of the same base version in the deployment repository.
     * 
     * @param developmentResource
     *            The development resource
     * @return The deployed resource
     * @throws Exception
     */
    private Resource deploySnapshotResource(Resource developmentResource) throws Exception {
        Version releasedBaseVersion = getReleasedBaseVersion(developmentResource);
        Resource snapshotResource = getHighestSnapshotResource(developmentResource, releasedBaseVersion);

        if (snapshotResource == null) {
            System.out.println("Uploading initial snapshot: " + getString(developmentResource) + " -> " + getNextSnapshotVersion(releasedBaseVersion));
            return deploySnapshotResource(developmentResource, getNextSnapshotVersion(releasedBaseVersion));
        }

        System.out.println("Found existing snapshot: " + getString(snapshotResource));
        if (getIdentity(developmentResource).equals("com.google.guava")) {
            // FIXME workaround for BND#374
            System.out.println("Skipping snapshot diff on Google Guava to work around https://github.com/bndtools/bnd/issues/374");
            return snapshotResource;
        }

        File developmentFile = m_developmentRepo.get(getIdentity(developmentResource), getVersion(developmentResource).toString(), Strategy.EXACT, null);
        File deployedFile = m_deploymentRepo.get(getIdentity(snapshotResource), getVersion(snapshotResource).toString(), Strategy.EXACT, null);

        boolean snapshotModified = false;
        if (getType(developmentResource).equals("osgi.bundle")) {

            // Get a copy of the dep resource with the same version as the dev resource so we can diff diff.
            File comparableDeployedResource = getBundleWithNewVersion(deployedFile, getVersion(developmentResource).toString());

            // This may seem strange but the value in the dev resource manifest may be "0" which will not match
            // "0.0.0" during diff.
            File comparableDevelopmentResource = getBundleWithNewVersion(developmentFile, getVersion(developmentResource).toString());
            snapshotModified = jarsDiffer(comparableDeployedResource, comparableDevelopmentResource);
        }
        else {
            snapshotModified = filesDiffer(developmentFile, deployedFile);
        }

        if (snapshotModified) {
            System.out.println("Uploading new snapshot: " + getString(developmentResource) + " -> " + getNextSnapshotVersion(getVersion(snapshotResource)));
            return deploySnapshotResource(developmentResource, getNextSnapshotVersion(getVersion(snapshotResource)));
        }
        else {
            System.out.println("Ignoring new snapshot: " + getString(developmentResource));
            List<Resource> resultResources = findResources(m_deploymentRepo, getIdentityVersionRequirement(snapshotResource));
            if (resultResources == null || resultResources.size() == 0) {
                throw new IllegalStateException("Can not find target resource after put: " + developmentResource);
            }
            return resultResources.get(0);
        }
    }

    private Resource deploySnapshotResource(Resource resource, Version snapshotVersion) throws Exception {
        File file = m_developmentRepo.get(getIdentity(resource), getVersion(resource).toString(), Strategy.EXACT, null);
        if (getType(resource).equals("osgi.bundle") || getType(resource).equals("osgi.fragment")) {
            file = getBundleWithNewVersion(file, snapshotVersion.toString());
        }

        InputStream input = null;
        try {
            input = new FileInputStream(file);
            if (m_deploymentRepo instanceof AceObrRepository) {
                // ACE OBR can handle non-bundle resources if we pass a correct filename
                AceObrRepository aceToRepo = (AceObrRepository) m_deploymentRepo;
                aceToRepo.upload(input, getFileName(resource, snapshotVersion), getMimetype(resource));
            }
            else {
                m_deploymentRepo.put(input, null);
            }
            m_deploymentRepo.reset();

            List<Resource> resultResources = findResources(m_deploymentRepo, getIdentity(resource), snapshotVersion.toString());
            if (resultResources == null || resultResources.size() == 0) {
                throw new IllegalStateException("Can not find target resource after put: " + resource);
            }
            return resultResources.get(0);

        }
        finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private Resource getHighestSnapshotResource(Resource resource, Version base) throws Exception {
        List<Resource> resources = findResources(m_deploymentRepo, getIdentity(resource));
        Resource matchedResource = null;
        for (Resource candidateResource : resources) {
            Version candidateVersion = getVersion(candidateResource);
            if (isSnapshotVersion(candidateVersion) && isSameBaseVersion(getVersion(candidateResource), base)
                && (matchedResource == null || getVersion(matchedResource).compareTo(getVersion(candidateResource)) < 0)) {
                matchedResource = candidateResource;
            }
        }
        return matchedResource;
    }

    private Version getReleasedBaseVersion(Resource resource) throws Exception {
        List<Resource> resources = findResources(m_releaseRepo, getIdentity(resource));
        Version resourceVersion = getVersion(resource);
        Version baseVersion = Version.emptyVersion;
        for (Resource candidate : resources) {
            Version candidateVersion = getVersion(candidate);
            if (candidateVersion.compareTo(resourceVersion) < 0) {
                if (candidateVersion.compareTo(baseVersion) > 0) {
                    baseVersion = candidateVersion;
                }
            }
        }
        return baseVersion;
    }
}
