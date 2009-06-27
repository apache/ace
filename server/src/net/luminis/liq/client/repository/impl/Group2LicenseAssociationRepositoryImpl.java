package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.LicenseObject;
import net.luminis.liq.client.repository.repository.Group2LicenseAssociationRepository;

import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Group2LicenseAssociationRepository. For 'what it does', see Group2LicenseAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Group2LicenseAssociationRepositoryImpl extends AssociationRepositoryImpl<GroupObject, LicenseObject, Group2LicenseAssociationImpl, Group2LicenseAssociation> implements Group2LicenseAssociationRepository {
    private final static String XML_NODE = "groups2licenses";

    private final GroupRepositoryImpl m_groupRepository;
    private final LicenseRepositoryImpl m_licenseRepository;

    public Group2LicenseAssociationRepositoryImpl(GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository, ChangeNotifier notifier) {
        super(notifier, XML_NODE);
        m_groupRepository = groupRepository;
        m_licenseRepository = licenseRepository;
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Group2LicenseAssociationImpl(attributes, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Group2LicenseAssociationImpl(attributes, tags, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Group2LicenseAssociationImpl(reader, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

}
