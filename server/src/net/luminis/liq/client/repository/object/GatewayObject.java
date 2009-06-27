package net.luminis.liq.client.repository.object;

import java.util.List;

import net.luminis.liq.client.repository.RepositoryObject;

public interface GatewayObject extends RepositoryObject {
    public static final String KEY_ID = "id";
    public static final String KEY_AUTO_APPROVE = "autoapprove";

    public static final String TOPIC_ENTITY_ROOT = GatewayObject.class.getSimpleName() + "/";
    
    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Returns all <code>LicenseObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<LicenseObject> getLicenses();
    /**
     * Returns all associations this gateway has with a given license.
     */
    public List<License2GatewayAssociation> getAssociationsWith(LicenseObject license);
    /**
     * Gets the ID of this GatewayObject.
     */
    public String getID();

    /**
     * Enable or disable automatic approval.
     */
    public void setAutoApprove(boolean approve);

    /**
     * Get the auto approval value of this gateway.
     */
    public boolean getAutoApprove();
}
