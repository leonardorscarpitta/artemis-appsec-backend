package io.github.artemiscore.artemisappsec.policy.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.StageGate;

public interface StageGateRepository extends JpaRepository<StageGate, UUID> {

	List<StageGate> findByOrganizationId(UUID organizationId);
}
