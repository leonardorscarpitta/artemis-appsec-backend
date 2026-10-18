package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Role;

/** A Keycloak account and its Artemis memberships; {@code id} is the Keycloak user id (the token's {@code sub}). */
public record AdminUserView(String id, String username, String name, String email, Instant createdAt, boolean enabled,
		boolean platformAdmin, List<Membership> organizations) {

	public record Membership(String slug, String name, Role role) {
	}
}
