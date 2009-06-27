package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.Artifact2GroupAssociation;
import net.luminis.liq.client.repository.object.ArtifactObject;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.repository.Artifact2GroupAssociationRepository;

import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Artifact2GroupAssociationRepository. For 'what it does', see Artifact2GroupAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Artifact2GroupAssociationRepositoryImpl extends AssociationRepositoryImpl<ArtifactObject, GroupObject, Artifact2GroupAssociationImpl, Artifact2GroupAssociation> implements Artifact2GroupAssociationRepository {
    private final static String XML_NODE = "artifacts2groups";

    private final ArtifactRepositoryImpl m_bundleRepository;
    private final GroupRepositoryImpl m_groupRepository;

    public Artifact2GroupAssociationRepositoryImpl(ArtifactRepositoryImpl bundleRepository, GroupRepositoryImpl groupRepository, ChangeNotifier notifier) {
        super(notifier, XML_NODE);
        m_bundleRepository = bundleRepository;
        m_groupRepository = groupRepository;
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Artifact2GroupAssociationImpl(attributes, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Artifact2GroupAssociationImpl(attributes, tags, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Artifact2GroupAssociationImpl(reader, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }
}
