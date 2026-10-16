package io.github.artemiscore.artemis.organization.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemis.organization.domain.Role;

public record MemberView(UUID id, String name, String email, Role role, boolean active, Instant lastActiveAt) {
}
