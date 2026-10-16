package io.github.artemiscore.artemis.organization.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.organization.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	List<Invitation> findAllByOrderBySentAtDesc();

	boolean existsByEmailIgnoreCase(String email);
}
