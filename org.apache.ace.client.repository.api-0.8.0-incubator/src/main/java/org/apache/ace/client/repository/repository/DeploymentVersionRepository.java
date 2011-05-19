package org.apache.ace.client.repository.repository;

import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;


/**
 * Interface to a DeploymentVersionRepository. The functionality is defined by the generic ObjectRepository.
 */
public interface DeploymentVersionRepository extends ObjectRepository<DeploymentVersionObject> {
    /**
     * Creates a new inhabitant based on the given attributes and bundle URLs. The object
     * will be stored in this repository's store, and will be returned.
     * @throws IllegalArgumentException Will be thrown when the attributes cannot be accepted.
     */
    public DeploymentVersionObject create(Map<String, String> attributes, Map<String, String> tags, DeploymentArtifact[] artifacts);

    /**
     * Gets all available deployment versions for this gateway. If none can be
     * found, an empty list will be returned.
     * @param gatewayID The gateway to be used.
     * @return A list of <code>DeploymentVersionObject</code>s which are related to
     * this gateway, sorted lexically by version.
     */
    public List<DeploymentVersionObject> getDeploymentVersions(String gatewayID);

    /**
     * Get the most recent known deployment version for a given gateway.
     * @param gatewayID The gateway to be used.
     * @return A <code>DeploymentVersionObject</code> which is the most recent one to be deployed
     * to the gateway. If none can be found, <code>null</code> will be returned.
     */
    public DeploymentVersionObject getMostRecentDeploymentVersion(String gatewayID);

    /**
     * Creates a DeploymentArtifact object.
     * @param url The url to be used in this object.
     * @param directives A map of directives to be packed into the object.
     * @return The newly created deployment artifact object.
     */
    public DeploymentArtifact createDeploymentArtifact(String url, Map<String, String> directives);
}
