package net.luminis.liq.client.repository.object;

import net.luminis.liq.client.repository.Association;

/**
 * Interface to a Group2LicenseAssociation. Most functionality is defined by the generic Association.
 */
public interface Group2LicenseAssociation extends Association<GroupObject, LicenseObject> {
    public static final String TOPIC_ENTITY_ROOT = Group2LicenseAssociation.class.getSimpleName() + "/";
    
    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;
}
