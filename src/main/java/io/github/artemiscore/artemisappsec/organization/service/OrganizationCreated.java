package io.github.artemiscore.artemisappsec.organization.service;

import java.util.UUID;

/** Published inside the creating transaction so other modules provision their defaults (plan, policies). */
public record OrganizationCreated(UUID organizationId) {
}
