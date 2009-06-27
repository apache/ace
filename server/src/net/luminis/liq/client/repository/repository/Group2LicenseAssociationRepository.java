package net.luminis.liq.client.repository.repository;

import net.luminis.liq.client.repository.AssociationRepository;
import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.LicenseObject;

/**
 * Interface to a Group2LicenseAssociationRepository. The functionality is defined by the generic AssociationRepository.
 */
public interface Group2LicenseAssociationRepository extends AssociationRepository<GroupObject, LicenseObject, Group2LicenseAssociation> {
}
