package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.UUID;

/** Public part of a repository's Artemis-generated deploy key. */
public record RepositoryKey(UUID id, String publicKey, String fingerprint, Instant createdAt, Instant lastUsedAt) {
}
