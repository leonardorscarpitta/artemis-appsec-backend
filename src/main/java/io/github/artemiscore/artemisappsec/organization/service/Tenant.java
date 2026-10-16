package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/**
 * The organization a request acts on and the caller's membership in it. {@code memberId} is null only when
 * authentication is disabled (local development). {@code actor} is the caller's username, for audit fields.
 */
public record Tenant(UUID organizationId, String slug, UUID memberId, Role role, String actor) {

	public boolean can(Permission permission) {
		return role.permissions().contains(permission);
	}

	public void require(Permission permission) {
		if (!can(permission)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Your role (%s) does not allow this action".formatted(role.wireValue()),
					Map.of("permission", permission.code()));
		}
	}
}
