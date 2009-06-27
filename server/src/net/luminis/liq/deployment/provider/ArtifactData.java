package net.luminis.liq.deployment.provider;

import java.net.URL;
import java.util.jar.Attributes;

/**
 * The ArtifactData as returned by the <code>DeploymentProvider</code> class in this package.
 * It contains several pieces of data which describe the artifact and the place where it can be found.
 */
public interface ArtifactData {

    /**
     * Indicate if the bundle has changed. This is used when comparing artifacts in 2 versions. (see DeploymentProvider)
     * If you requested one version it always returns true.
     *
     * @return if this artifact has changed.
     */
    public boolean hasChanged();

    /**
     * @return <code>true</code> if this artifact is a bundle; <code>false</code> otherwise.
     */
    public boolean isBundle();

    /**
     * @return <code>true</code> if this artifact is a customizer that contains a resource processor; <code>false</code> otherwise.
     */
    public boolean isCustomizer();

    /**
     * @return the filename of the artifact
     */
    public String getFilename();

    /**
     *  @return the symbolic name, if this artifact is a bundle.
     */
    public String getSymbolicName();

    /**
     *  @return the version, if this artifact is a bundle. If it is an artifact, this function
     *  will always return "0.0.0".
     */
    public String getVersion();

    /**
     * @return the url to the artifact data.
     */
    public URL getUrl();

    /**
     * @return the processor Pid to be used for this resource, if any.
     */
    public String getProcessorPid();

    /**
     * @return a set of attributes that describes this artifact in a manifest.
     * @param fixPackage Indicating whether this set of headers is intended to be part
     * of a fixpackage.
     */
    public Attributes getManifestAttributes(boolean fixPackage);

}