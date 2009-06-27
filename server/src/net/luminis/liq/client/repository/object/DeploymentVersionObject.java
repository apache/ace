package net.luminis.liq.client.repository.object;

import net.luminis.liq.client.repository.RepositoryObject;

/**
 * The interface to a DeploymentVersion. The basic functionality is defined
 * by RepositoryObject, but extended for deployment version-specific information.
 *
 * DeploymentVersions need some additional information about the artifacts they
 * are associated with; see DeploymentArtifact.
 */
public interface DeploymentVersionObject extends RepositoryObject {

    public static final String KEY_GATEWAYID = "gatewayID";
    public static final String KEY_VERSION = "version";
    
    public static final String TOPIC_ENTITY_ROOT = DeploymentVersionObject.class.getSimpleName() + "/";
    
    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Gets the gateway which is related to this version.
     */
    public String getGatewayID();

    /**
     * Gets the version number of this deployment version.
     */
    public String getVersion();

    /**
     * @return an array of all deployment artifacts that will be part of this deployment version.
     * The order of the artifacts in the array is equal to the order they should appear in a 
     * deployment package.
     */
    public DeploymentArtifact[] getDeploymentArtifacts();
}
