package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.UUID;

public record RemovedRepository(UUID id, String name, Instant removedAt) {
}
