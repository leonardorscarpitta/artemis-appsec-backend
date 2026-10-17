package io.github.artemiscore.artemisappsec.finding.service;

import java.time.LocalDate;
import java.util.UUID;

/** Published when a finding is accepted; the policy module records the risk acceptance. */
public record RiskAccepted(UUID organizationId, UUID findingId, UUID projectId, String cve, String justification,
		LocalDate expiresAt, String actor) {
}
