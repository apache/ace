package net.luminis.liq.client.repository.repository;

import net.luminis.liq.client.repository.AssociationRepository;
import net.luminis.liq.client.repository.object.Artifact2GroupAssociation;
import net.luminis.liq.client.repository.object.ArtifactObject;
import net.luminis.liq.client.repository.object.GroupObject;

/**
 * Interface to a Artifact2GroupAssociationRepository. The functionality is defined by the generic AssociationRepository.
 */
public interface Artifact2GroupAssociationRepository extends AssociationRepository<ArtifactObject, GroupObject, Artifact2GroupAssociation> {
}
