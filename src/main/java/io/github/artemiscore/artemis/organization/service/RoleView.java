package io.github.artemiscore.artemis.organization.service;

import java.util.List;

import io.github.artemiscore.artemis.organization.domain.Permission;
import io.github.artemiscore.artemis.organization.domain.Role;

public record RoleView(Role role, List<Permission> permissions, boolean assignable) {
}
