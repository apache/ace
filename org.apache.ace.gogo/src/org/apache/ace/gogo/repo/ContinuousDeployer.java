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
import java.util.List;

import org.osgi.framework.Version;
import org.osgi.resource.Resource;

import aQute.bnd.deployer.repository.FixedIndexedRepo;
import aQute.bnd.service.Strategy;

public class ContinuousDeployer {

    FixedIndexedRepo m_deploymentRepo;
    FixedIndexedRepo m_developmentRepo;
    FixedIndexedRepo m_releaseRepo;

    public ContinuousDeployer(FixedIndexedRepo deploymentRepo, FixedIndexedRepo developmentRepo, FixedIndexedRepo releaseRepo) {
        m_deploymentRepo = deploymentRepo;
        m_developmentRepo = developmentRepo;
        m_releaseRepo = releaseRepo;
    }

    /**
     * Deploys all resources from the development repository into the deployment repository.
     * 
     * @return
     * @throws Exception
     */
    public List<Resource> deployResources() throws Exception {
        List<Resource> resources = findResources(m_developmentRepo, "*", "*");
        for (Resource resource : resources) {
            // FIXME this is the source resource
            deployResource(resource);
        }
        return resources;
    }

    /**
     * Deploys a resource to the deployment repository.
     * 
     * @param resource
     *            The resource
     * @throws Exception
     *             On failure
     */
    private void deployResource(Resource resource) throws Exception {
        List<Resource> releaseResources = findResources(m_releaseRepo, getIdentity(resource), getVersion(resource).toString());
        boolean isReleased = releaseResources.size() > 0;
        if (isReleased) {
            deployReleasedResource(resource);
        }
        else {
            deploySnapshotResource(resource);
        }
    }

    /**
     * Deploys a released resource from the release repository to the deployment repository if it has not been deployed
     * yet.
     * 
     * @param resource
     *            The resource
     * @throws Exception
     *             On failure
     */
    private void deployReleasedResource(Resource resource) throws Exception {
        List<Resource> deployedResources = findResources(m_deploymentRepo, getIdentity(resource), getVersion(resource).toString());
        boolean isDeployed = deployedResources.size() > 0;
        if (!isDeployed) {
            System.out.println("Uploading released resource:  " + getString(resource));
            copyResources(m_releaseRepo, m_deploymentRepo, getIdentityVersionRequirement(resource));
        }
        else {
            System.out.println("Released resource allready deployed:  " + getString(resource));
        }
    }

    /**
     * Deploys a snapshot resource to the deployment repository if it differs from the highest existing snapshot
     * resource of the same base version in the deployment repository.
     * 
     * @param resource
     *            The resource
     * @throws Exception
     *             On failure
     */
    private void deploySnapshotResource(Resource resource) throws Exception {

        Version releasedBaseVersion = getReleasedBaseVersion(resource);
        Resource snapshotResource = getHighestSnapshotResource(resource, releasedBaseVersion);
        if (snapshotResource == null) {
            System.out.println("Uploading initial snapshot:  " + getString(resource) + " -> " + getNextSnapshotVersion(releasedBaseVersion));
            deploySnapshotResource(resource, getNextSnapshotVersion(releasedBaseVersion));
        }
        else {
            System.out.println("Found existing snapshot:  " + getString(snapshotResource));

            // FIXME workaround for BND#374
            if (getIdentity(resource).equals("com.google.guava")) {
                System.out.println("Skipping snapshot diff on Google Guava to work around https://github.com/bndtools/bnd/issues/374");
                return;
            }

            File developmentResource = m_developmentRepo.get(getIdentity(resource), getVersion(resource).toString(), Strategy.EXACT, null);
            File deployedResource = m_deploymentRepo.get(getIdentity(snapshotResource), getVersion(snapshotResource).toString(), Strategy.EXACT, null);

            boolean snapshotModified = false;

            if (getType(resource).equals("osgi.bundle")) {

                // Get a copy of the dep resource with the same version as the dev resource so we can diff diff.
                File comparableDeployedResource = getBundleWithNewVersion(deployedResource, getVersion(resource).toString());

                // This may seem strange but the value in the dev resource manifest may be "0" which will not match
                // "0.0.0" during diff.
                File comparableDevelopmentResource = getBundleWithNewVersion(developmentResource, getVersion(resource).toString());
                snapshotModified = jarsDiffer(comparableDeployedResource, comparableDevelopmentResource);
            }
            else {
                snapshotModified = filesDiffer(developmentResource, deployedResource);
            }

            if (snapshotModified) {
                System.out.println("Uploading new snapshot:  " + getString(resource) + " -> " + getNextSnapshotVersion(getVersion(snapshotResource)));
                deploySnapshotResource(resource, getNextSnapshotVersion(getVersion(snapshotResource)));
            }
            else {
                System.out.println("Ignoring new snapshot:  " + getString(resource));
            }
        }
    }

    private void deploySnapshotResource(Resource resource, Version snapshotVersion) throws Exception {

        File file = m_developmentRepo.get(getIdentity(resource), getVersion(resource).toString(), Strategy.EXACT, null);
        if (getType(resource).equals("osgi.bundle")) {
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

        }
        finally {
            if (input != null)
                input.close();
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
