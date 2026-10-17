package io.github.artemiscore.artemisappsec.policy.service;

import java.time.LocalDate;
import java.util.UUID;

/** {@code findingId}, {@code reference} and {@code title} are null for acceptances not linked to a finding. */
public record RiskAcceptanceView(UUID id, UUID findingId, String reference, String title, String cve, String repository,
		String justification, String approvedBy, LocalDate expiresAt, boolean expired) {
}
