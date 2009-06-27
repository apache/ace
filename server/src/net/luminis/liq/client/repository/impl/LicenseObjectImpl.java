package net.luminis.liq.client.repository.impl;

import java.util.List;
import java.util.Map;

import net.luminis.liq.client.repository.object.GatewayObject;
import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.License2GatewayAssociation;
import net.luminis.liq.client.repository.object.LicenseObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the LicenseObject. For 'what it does', see LicenseObject,
 * for 'how it works', see RepositoryObjectImpl.
 */
public class LicenseObjectImpl extends RepositoryObjectImpl<LicenseObject> implements LicenseObject {
    private final static String XML_NODE = "license";

    LicenseObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), tags, notifier, XML_NODE);
    }

    LicenseObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), notifier, XML_NODE);
    }

    LicenseObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
    }

    public List<GatewayObject> getGateways() {
        return getAssociations(GatewayObject.class);
    }

    public List<GroupObject> getGroups() {
        return getAssociations(GroupObject.class);
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

    public List<Group2LicenseAssociation> getAssociationsWith(GroupObject group) {
        return getAssociationsWith(group, GroupObject.class, Group2LicenseAssociation.class);
    }

    public List<License2GatewayAssociation> getAssociationsWith(GatewayObject gateway) {
        return getAssociationsWith(gateway, GatewayObject.class, License2GatewayAssociation.class);
    }

    private static String[] DEFINING_KEYS = new String[] {KEY_NAME};
    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }

}
