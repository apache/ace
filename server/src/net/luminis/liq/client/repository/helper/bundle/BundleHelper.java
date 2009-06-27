package net.luminis.liq.client.repository.helper.bundle;

import net.luminis.liq.client.repository.helper.ArtifactHelper;
import net.luminis.liq.client.repository.object.ArtifactObject;

import org.osgi.framework.Constants;

/**
 * Definitions for a BundleHelper, which are used to treat an artifact as a bundle.
 */
public interface BundleHelper extends ArtifactHelper {
    public static final String KEY_SYMBOLICNAME = Constants.BUNDLE_SYMBOLICNAME;
    public static final String KEY_NAME = Constants.BUNDLE_NAME;
    public static final String KEY_VERSION = Constants.BUNDLE_VERSION;
    public static final String KEY_VENDOR = Constants.BUNDLE_VENDOR;
    public static final String KEY_RESOURCE_PROCESSOR_PID = "Deployment-ProvidesResourceProcessor";

    public static final String MIMETYPE = "application/vnd.osgi.bundle";

    /**
     * Used to include an OSGi version range (see section 3.2.5 of the core specification) with an association.
     * When included in the association's properties, this statement will cause the association to automatically
     * match the highest available bundle version that matches the statement; an open ended range can be
     * created by passing "0.0.0".<br>
     * Not specifying this attribute will lead to the <code>Artifact2GroupAssociation</code<'s default behavior,
     * using all matches for the filter string.
     */
    public static final String KEY_ASSOCIATION_VERSIONSTATEMENT = "associationVersionStatement";
    
    public boolean isResourceProcessor(ArtifactObject object);
    public String getResourceProcessorPIDs(ArtifactObject object);
    public String getSymbolicName(ArtifactObject object);
    public String getName(ArtifactObject object);
    public String getVersion(ArtifactObject object);
    public String getVendor(ArtifactObject object);
    
}
