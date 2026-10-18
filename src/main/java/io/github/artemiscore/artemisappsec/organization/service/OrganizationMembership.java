package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

/** An organization the caller belongs to, with the caller's role there. {@code features} are the optional features enabled for it. */
public record OrganizationMembership(String slug, String name, Role role, List<Permission> permissions, List<Feature> features) {
}
