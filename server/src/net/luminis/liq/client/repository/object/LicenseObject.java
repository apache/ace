package net.luminis.liq.client.repository.object;

import java.util.List;

import net.luminis.liq.client.repository.RepositoryObject;

/**
 * Interface to a LicenseObject. The basic functionality is defined by RepositoryObject, but extended for
 * License-specific information.
 */
public interface LicenseObject extends RepositoryObject {
    public static final String TOPIC_ENTITY_ROOT = LicenseObject.class.getSimpleName() + "/";
    
    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_NAME = "name";

    /**
     * Returns all <code>GroupObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<GroupObject> getGroups();
    /**
     * Returns all <code>GatewayObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<GatewayObject> getGateways();

    /**
     * Returns all associations this license has with a given group.
     */
    public List<Group2LicenseAssociation> getAssociationsWith(GroupObject group);
    /**
     * Returns all associations this license has with a given gateway.
     */
    public List<License2GatewayAssociation> getAssociationsWith(GatewayObject gateway);

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
