package io.github.artemiscore.artemis.policy.service;

import java.time.LocalDate;
import java.util.UUID;

public record RiskAcceptanceView(UUID id, String cve, String repository, String justification, String approvedBy,
		LocalDate expiresAt) {
}
