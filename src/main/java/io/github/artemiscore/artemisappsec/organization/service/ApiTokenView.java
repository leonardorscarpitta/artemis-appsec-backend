package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.TokenScope;

/** {@code hint} shows only the token prefix and its last four characters. */
public record ApiTokenView(UUID id, String name, List<TokenScope> scopes, String hint, Instant createdAt,
		Instant expiresAt, Instant lastUsedAt, boolean expired) {
}
