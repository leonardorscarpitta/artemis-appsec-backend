package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

/**
 * The organization's role → permission matrix. Invariants are applied on read so stored rows can never weaken
 * them: the owner has every permission, every role can view findings and only the owner has billing (ownership and deletion).
 */
@Repository
public class RolePermissionStore {

	private final JdbcClient jdbc;

	public RolePermissionStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Set<Permission> permissionsOf(UUID organizationId, Role role) {
		if (role == Role.OWNER) {
			return EnumSet.allOf(Permission.class);
		}
		Set<Permission> stored = EnumSet.noneOf(Permission.class);
		jdbc.sql("SELECT permission FROM organization_role_permission WHERE organization_id = :org AND role = :role")
			.param("org", organizationId)
			.param("role", role.name())
			.query(String.class)
			.list()
			.forEach(p -> stored.add(Permission.valueOf(p)));
		return enforce(role, stored.isEmpty() ? role.defaultPermissions() : stored);
	}

	public Map<Role, Set<Permission>> matrix(UUID organizationId) {
		Map<Role, Set<Permission>> matrix = new EnumMap<>(Role.class);
		for (Role role : Role.values()) {
			matrix.put(role, permissionsOf(organizationId, role));
		}
		return matrix;
	}

	public void replace(UUID organizationId, Role role, Collection<Permission> permissions) {
		jdbc.sql("DELETE FROM organization_role_permission WHERE organization_id = :org AND role = :role")
			.param("org", organizationId)
			.param("role", role.name())
			.update();
		for (Permission permission : enforce(role, permissions)) {
			jdbc.sql("INSERT INTO organization_role_permission (organization_id, role, permission) VALUES (:org, :role, :permission)")
				.param("org", organizationId)
				.param("role", role.name())
				.param("permission", permission.name())
				.update();
		}
	}

	public void provisionDefaults(UUID organizationId) {
		for (Role role : Role.values()) {
			if (role != Role.OWNER) {
				replace(organizationId, role, role.defaultPermissions());
			}
		}
	}

	private static Set<Permission> enforce(Role role, Collection<Permission> permissions) {
		Set<Permission> result = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
		result.add(Permission.VIEW_FINDINGS);
		if (role != Role.OWNER) {
			result.remove(Permission.BILLING);
		}
		return result;
	}
}
