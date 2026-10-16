package io.github.artemiscore.artemis.organization.domain;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
	OWNER(EnumSet.allOf(Permission.class)),
	ADMIN(EnumSet.of(Permission.VIEW_FINDINGS, Permission.TRIAGE, Permission.PIPELINES, Permission.INTEGRATIONS, Permission.TEAM)),
	ANALYST(EnumSet.of(Permission.VIEW_FINDINGS, Permission.TRIAGE, Permission.PIPELINES)),
	DEVELOPER(EnumSet.of(Permission.VIEW_FINDINGS, Permission.TRIAGE)),
	VIEWER(EnumSet.of(Permission.VIEW_FINDINGS));

	private final Set<Permission> permissions;

	Role(Set<Permission> permissions) {
		this.permissions = permissions;
	}

	public Set<Permission> permissions() {
		return Set.copyOf(permissions);
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
