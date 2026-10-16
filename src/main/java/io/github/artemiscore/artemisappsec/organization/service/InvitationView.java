package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.Role;

public record InvitationView(UUID id, String email, Role role, Instant sentAt) {
}
