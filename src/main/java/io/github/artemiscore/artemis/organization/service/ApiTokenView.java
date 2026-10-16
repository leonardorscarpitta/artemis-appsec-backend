package io.github.artemiscore.artemis.organization.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemis.organization.domain.TokenScope;

/** {@code hint} shows only the token prefix and its last four characters. */
public record ApiTokenView(UUID id, String name, List<TokenScope> scopes, String hint, Instant createdAt,
		Instant expiresAt, Instant lastUsedAt, boolean expired) {
}
