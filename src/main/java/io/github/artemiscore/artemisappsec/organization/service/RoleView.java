package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

/**
 * A role and its permissions in the caller's organization. {@code assignable}: can be given to members (not owner).
 * {@code editable}: the caller may change this role's permissions.
 */
public record RoleView(Role role, List<Permission> permissions, boolean assignable, boolean editable) {
}
