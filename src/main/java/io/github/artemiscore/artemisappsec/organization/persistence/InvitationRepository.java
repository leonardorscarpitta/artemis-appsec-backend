package io.github.artemiscore.artemisappsec.organization.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	List<Invitation> findByOrganizationIdOrderBySentAtDesc(UUID organizationId);

	Optional<Invitation> findByIdAndOrganizationId(UUID id, UUID organizationId);

	Optional<Invitation> findByOrganizationIdAndEmail(UUID organizationId, String email);

	List<Invitation> findByEmailAndExpiresAtAfterOrderBySentAtDesc(String email, Instant now);
}
