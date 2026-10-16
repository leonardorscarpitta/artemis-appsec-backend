package io.github.artemiscore.artemis.policy.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.policy.domain.RiskAcceptance;

public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, UUID> {

	List<RiskAcceptance> findAllByOrderByExpiresAtAsc();

	boolean existsByCveAndProjectId(String cve, UUID projectId);
}
