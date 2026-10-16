package io.github.artemiscore.artemis.organization.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemis.organization.domain.Role;

public record InvitationView(UUID id, String email, Role role, Instant sentAt) {
}
