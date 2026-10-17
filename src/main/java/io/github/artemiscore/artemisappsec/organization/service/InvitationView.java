package io.github.artemiscore.artemisappsec.organization.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.Role;

/** {@code token} is the one-time link token, present only in the create/resend response. */
public record InvitationView(UUID id, String email, Role role, String invitedBy, Instant sentAt, Instant expiresAt,
		boolean expired, @JsonInclude(JsonInclude.Include.NON_NULL) String token) {
}
