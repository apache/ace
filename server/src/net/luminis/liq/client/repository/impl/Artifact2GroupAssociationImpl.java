package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.Artifact2GroupAssociation;
import net.luminis.liq.client.repository.object.ArtifactObject;
import net.luminis.liq.client.repository.object.GroupObject;

import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Artifact2GroupAssociation. For 'what it does', see Artifact2GroupAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class Artifact2GroupAssociationImpl extends AssociationImpl<ArtifactObject, GroupObject, Artifact2GroupAssociation> implements Artifact2GroupAssociation {
    private final static String XML_NODE = "artifact2group";

    public Artifact2GroupAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, GroupRepositoryImpl groupRepository) throws InvalidSyntaxException {
        super(attributes, notifier, ArtifactObject.class, GroupObject.class, artifactRepository, groupRepository, XML_NODE);
    }

    public Artifact2GroupAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, GroupRepositoryImpl groupRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, ArtifactObject.class, GroupObject.class, artifactRepository, groupRepository, XML_NODE);
    }

    public Artifact2GroupAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, GroupRepositoryImpl groupRepository) throws InvalidSyntaxException {
        super(reader, notifier, ArtifactObject.class, GroupObject.class, null, null, artifactRepository, groupRepository, XML_NODE);
    }

}
