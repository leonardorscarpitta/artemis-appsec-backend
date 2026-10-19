package io.github.artemiscore.artemisappsec.organization.service;

import io.github.artemiscore.artemisappsec.organization.domain.Role;

/** What an invitation link offers, shown before the user decides to join. */
public record InvitationPreview(String organizationName, String organizationSlug, Role role) {
}
