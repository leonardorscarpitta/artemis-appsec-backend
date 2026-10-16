package io.github.artemiscore.artemis.organization.service;

import java.time.Instant;

import io.github.artemiscore.artemis.organization.domain.IntegrationCategory;
import io.github.artemiscore.artemis.organization.domain.IntegrationStatus;

/** {@code removable} is false for integrations the platform depends on (e.g. NIST NVD). */
public record IntegrationView(String id, String name, String shortCode, IntegrationCategory category,
		IntegrationStatus status, String detail, boolean removable, Instant lastSyncAt) {
}
