package io.github.artemiscore.artemisappsec.policy.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.RiskAcceptance;

public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, UUID> {

	List<RiskAcceptance> findByProjectIdInOrderByExpiresAtAsc(Collection<UUID> projectIds);

	Optional<RiskAcceptance> findByFindingId(UUID findingId);

	List<RiskAcceptance> findByExpiresAtBefore(LocalDate date);
}
