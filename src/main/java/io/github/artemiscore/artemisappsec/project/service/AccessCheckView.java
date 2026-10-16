package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.project.domain.AccessCheckStatus;

public record AccessCheckView(UUID id, AccessCheckStatus status, String message, Instant requestedAt, Instant finishedAt) {
}
