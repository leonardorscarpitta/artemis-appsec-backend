package io.github.artemiscore.artemisappsec.insight.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.insight.domain.FindingInsight;

public interface FindingInsightRepository extends JpaRepository<FindingInsight, UUID> {

	Optional<FindingInsight> findByOrganizationIdAndCacheKeyAndLanguage(UUID organizationId, String cacheKey,
			String language);

	/** Any language, newest first: for when the organization changed its language after the explanation was made. */
	Optional<FindingInsight> findFirstByOrganizationIdAndCacheKeyOrderByCreatedAtDesc(UUID organizationId,
			String cacheKey);
}
