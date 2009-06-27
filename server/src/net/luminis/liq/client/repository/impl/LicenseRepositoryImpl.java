package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.LicenseObject;
import net.luminis.liq.client.repository.repository.LicenseRepository;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the LicenseRepository. For 'what it does', see LicenseRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 */
public class LicenseRepositoryImpl extends ObjectRepositoryImpl<LicenseObjectImpl, LicenseObject> implements LicenseRepository {
    private final static String XML_NODE = "licenses";

    public LicenseRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    @Override
    LicenseObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        return new LicenseObjectImpl(attributes, tags, this);
    }

    @Override
    LicenseObjectImpl createNewInhabitant(Map<String, String> attributes) {
        return new LicenseObjectImpl(attributes, this);
    }

    @Override
    LicenseObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new LicenseObjectImpl(reader, this);
    }

}
