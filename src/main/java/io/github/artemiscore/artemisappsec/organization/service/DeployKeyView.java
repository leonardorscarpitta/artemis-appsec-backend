package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.UUID;

public record DeployKeyView(UUID id, String name, String keyType, String fingerprint, String repository,
		Instant addedAt, Instant lastUsedAt) {
}
