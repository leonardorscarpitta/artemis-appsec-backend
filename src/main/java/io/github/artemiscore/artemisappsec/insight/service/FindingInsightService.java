package io.github.artemiscore.artemisappsec.insight.service;

import java.util.Optional;
import java.util.UUID;

/** AI explanations of findings: generated only on request and cached per organization. */
public interface FindingInsightService {

	/** The cached explanation for this finding, if any (no AI call). */
	Optional<FindingInsightView> find(UUID organizationId, UUID findingId);

	/**
	 * Returns the cached explanation or generates one. Needs the AI feature (403 {@code feature-not-enabled}), a
	 * configured AI key (503 {@code ai-unavailable}) and the daily limit not reached (429 {@code ai-limit}); an unusable
	 * answer is 502 {@code ai-failed} and nothing is cached.
	 */
	FindingInsightView generate(UUID organizationId, UUID findingId);
}
