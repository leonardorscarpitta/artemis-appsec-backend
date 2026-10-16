package io.github.artemiscore.artemisappsec.policy.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.policy.domain.RemediationSla;

public interface RemediationSlaRepository extends JpaRepository<RemediationSla, UUID> {

	List<RemediationSla> findByOrganizationId(UUID organizationId);
}
