package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.LicenseObject;

import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Group2LicenseAssociation. For 'what it does', see Group2LicenseAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class Group2LicenseAssociationImpl extends AssociationImpl<GroupObject, LicenseObject, Group2LicenseAssociation> implements Group2LicenseAssociation {
    private final static String XML_NODE = "group2license";

    public Group2LicenseAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(attributes, notifier, GroupObject.class, LicenseObject.class, groupRepository, licenseRepository, XML_NODE);
    }
    public Group2LicenseAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, GroupObject.class, LicenseObject.class, groupRepository, licenseRepository, XML_NODE);
    }
    public Group2LicenseAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(reader, notifier, GroupObject.class, LicenseObject.class, null, null, groupRepository, licenseRepository, XML_NODE);
    }

}
