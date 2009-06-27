package net.luminis.liq.client.repository.impl;

import java.util.List;
import java.util.Map;

import net.luminis.liq.client.repository.object.Artifact2GroupAssociation;
import net.luminis.liq.client.repository.object.ArtifactObject;
import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.LicenseObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the GroupObject. For 'what it does', see GroupObject,
 * for 'how it works', see RepositoryObjectImpl.
 */
public class GroupObjectImpl extends RepositoryObjectImpl<GroupObject> implements GroupObject {
    private final static String XML_NODE = "group";

    GroupObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), tags, notifier, XML_NODE);
    }

    GroupObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), notifier, XML_NODE);
    }

    GroupObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
    }

    public List<ArtifactObject> getArtifacts() {
        return getAssociations(ArtifactObject.class);
    }

    public List<LicenseObject> getLicenses() {
        return getAssociations(LicenseObject.class);
    }

    public String getDescription() {
        return getAttribute(KEY_DESCRIPTION);
    }

    public String getName() {
        return getAttribute(KEY_NAME);
    }

    public void setDescription(String description) {
        addAttribute(KEY_DESCRIPTION, description);
    }

    public void setName(String name) {
        addAttribute(KEY_NAME, name);
    }

    public List<Artifact2GroupAssociation> getAssociationsWith(ArtifactObject artifact) {
        return getAssociationsWith(artifact, ArtifactObject.class, Artifact2GroupAssociation.class);
    }

    public List<Group2LicenseAssociation> getAssociationsWith(LicenseObject license) {
        return getAssociationsWith(license, LicenseObject.class, Group2LicenseAssociation.class);
    }

    private static String[] DEFINING_KEYS = new String[] {KEY_NAME};
    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }

}
