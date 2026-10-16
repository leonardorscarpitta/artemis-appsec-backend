package io.github.artemiscore.artemisappsec.policy.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.RiskAcceptance;

public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, UUID> {

	List<RiskAcceptance> findByProjectIdInOrderByExpiresAtAsc(Collection<UUID> projectIds);

	boolean existsByCveAndProjectId(String cve, UUID projectId);
}
