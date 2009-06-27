package net.luminis.liq.client.repository.impl;

import java.util.Map;

import net.luminis.liq.client.repository.object.GatewayObject;
import net.luminis.liq.client.repository.object.License2GatewayAssociation;
import net.luminis.liq.client.repository.object.LicenseObject;

import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the License2GatewayAssociation. For 'what it does', see License2GatewayAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class License2GatewayAssociationImpl extends AssociationImpl<LicenseObject, GatewayObject, License2GatewayAssociation> implements License2GatewayAssociation {
    private final static String XML_NODE = "license2gateway";

    public License2GatewayAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(attributes, notifier, LicenseObject.class, GatewayObject.class, licenseRepository, gatewayRepository, XML_NODE);
    }
    public License2GatewayAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, LicenseObject.class, GatewayObject.class, licenseRepository, gatewayRepository, XML_NODE);
    }
    public License2GatewayAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, LicenseRepositoryImpl licenseRepository, GatewayRepositoryImpl gatewayRepository) throws InvalidSyntaxException {
        super(reader, notifier, LicenseObject.class, GatewayObject.class, null, null, licenseRepository, gatewayRepository, XML_NODE);
    }

}
