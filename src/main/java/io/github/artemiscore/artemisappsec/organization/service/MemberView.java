package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.Role;

public record MemberView(UUID id, String name, String email, Role role, boolean active, Instant lastActiveAt) {
}
