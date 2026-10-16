package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

/** An organization the caller belongs to, with the caller's role there. {@code plan} is the plan's wire code. */
public record OrganizationMembership(String slug, String name, Role role, List<Permission> permissions, String plan) {
}
