package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

public record RoleView(Role role, List<Permission> permissions, boolean assignable) {
}
