package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/**
 * The organization a request acts on and the caller's membership in it. {@code memberId} is null when
 * authentication is disabled (local development) and for Artemis administrators viewing an organization they are not
 * a member of (read-only, see {@link MembershipService#resolve}). {@code actor} is the caller's username, for audit fields.
 * {@code permissions} are the role's permissions in this organization.
 */
public record Tenant(UUID organizationId, String slug, UUID memberId, Role role, String actor, Set<Permission> permissions) {

	public boolean can(Permission permission) {
		return permissions.contains(permission);
	}

	public void require(Permission permission) {
		if (!can(permission)) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden",
					"Your role (%s) does not allow this action".formatted(role.wireValue()),
					Map.of("permission", permission.code()));
		}
	}
}
