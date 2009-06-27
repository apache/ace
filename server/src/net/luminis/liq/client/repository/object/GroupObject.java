package net.luminis.liq.client.repository.object;

import java.util.List;

import net.luminis.liq.client.repository.RepositoryObject;

/**
 * Interface to a GroupObject. The basic functionality is defined by RepositoryObject, but extended for
 * Group-specific information.
 */
public interface GroupObject extends RepositoryObject {
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_NAME = "name";

    public static final String TOPIC_ENTITY_ROOT = GroupObject.class.getSimpleName() + "/";
    
    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Returns all <code>ArtifactObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<ArtifactObject> getArtifacts();
    /**
     * Returns all <code>LicenseObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<LicenseObject> getLicenses();

    /**
     * Returns all associations this group has with a given bundle.
     */
    public List<Artifact2GroupAssociation> getAssociationsWith(ArtifactObject artifact);
    /**
     * Returns all associations this group has with a given license.
     */
    public List<Group2LicenseAssociation> getAssociationsWith(LicenseObject license);

    /**
     * Returns the name of this bundle.
     */
    public String getName();
    /**
     * Sets the name of this bundle.
     */
    public void setName(String name);
    /**
     * Returns the description of this bundle.
     */
    public String getDescription();
    /**
     * Sets the description of this bundle.
     */
    public void setDescription(String description);


}
