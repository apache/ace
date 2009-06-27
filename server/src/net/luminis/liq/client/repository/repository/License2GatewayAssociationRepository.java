package net.luminis.liq.client.repository.repository;

import net.luminis.liq.client.repository.AssociationRepository;
import net.luminis.liq.client.repository.object.GatewayObject;
import net.luminis.liq.client.repository.object.License2GatewayAssociation;
import net.luminis.liq.client.repository.object.LicenseObject;

/**
 * Interface to a License2GatewayAssociationRepository. The functionality is defined by the generic AssociationRepository.
 */
public interface License2GatewayAssociationRepository extends AssociationRepository<LicenseObject, GatewayObject, License2GatewayAssociation> {
    /**
     * Creates an assocation from a given license to multiple gateways, which correspond to the given
     * filter string. For parameters to use in the filter, see <code>GatewayObject</code>'s <code>KEY_</code> constants.
     * @param license A license object for the left side of this association.
     * @param gatewayFilter An LDAP-filter for the gateways to use.
     * @return The newly created association.
     */
    public License2GatewayAssociation createLicense2GatewayFilter(LicenseObject license, String gatewayFilter);
}
