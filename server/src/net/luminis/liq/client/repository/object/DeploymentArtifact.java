package net.luminis.liq.client.repository.object;

import java.net.URL;

/**
 * Interface to a deployment artifact, which is used to gather information about
 * the deployment of a single artifact.
 */
public interface DeploymentArtifact {

    /**
     * Key, intended to be used for artifacts which are bundles and will publish
     * a resource processor (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_ISCUSTOMIZER = "DeploymentPackage-Customizer";
    
    /**
     * Key, intended to be used for resources which require a resource processor
     * (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_KEY_PROCESSORID = "Resource-Processor";

    /**
     * Key, intended to be used for matching processed (see ArtifactPreprocessor) to their
     * 'original' one.
     */
    public static final String DIRECTIVE_KEY_BASEURL = "Base-Url";

    /**
     * @return the URL for this deployment artifact.
     */
    public String getUrl();
    
    /**
     * @param key A key String, such as the <code>DIRECTIVE_</code> constants in
     * <code>DeploymentArtifact</code>.
     * @return the value for the given directive key, or <code>null</code> if not found.
     */
    public String getDirective(String key);
    
    /**
     * @return an array of all keys that are used in this object, to be used in <code>getDirective</code>.
     */
    public String[] getKeys();

}
