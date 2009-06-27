package net.luminis.liq.client.repository.impl;

import java.util.Map;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

import net.luminis.liq.client.repository.object.GatewayObject;
import net.luminis.liq.client.repository.repository.GatewayRepository;

/**
 * Implementation class for the GatewayRepository. For 'what it does', see GatewayRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 */
public class GatewayRepositoryImpl extends ObjectRepositoryImpl<GatewayObjectImpl, GatewayObject> implements GatewayRepository {
    private final static String XML_NODE = "gateways";

    public GatewayRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        return new GatewayObjectImpl(attributes, tags, this);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(Map<String, String> attributes) {
        return new GatewayObjectImpl(attributes, this);
    }

    @Override
    GatewayObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new GatewayObjectImpl(reader, this);
    }

}
