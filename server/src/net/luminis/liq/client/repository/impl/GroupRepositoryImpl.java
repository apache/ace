package net.luminis.liq.client.repository.impl;

import java.util.Map;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.repository.GroupRepository;

/**
 * Implementation class for the GroupRepository. For 'what it does', see GroupRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 */
public class GroupRepositoryImpl extends ObjectRepositoryImpl<GroupObjectImpl, GroupObject> implements GroupRepository {
    private final static String XML_NODE = "groups";

    public GroupRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    @Override
    GroupObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        return new GroupObjectImpl(attributes, tags, this);
    }

    @Override
    GroupObjectImpl createNewInhabitant(Map<String, String> attributes) {
        return new GroupObjectImpl(attributes, this);
    }

    @Override
    GroupObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new GroupObjectImpl(reader, this);
    }

}
