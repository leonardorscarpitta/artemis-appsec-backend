package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Set;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

/** Optional features the Artemis administrators enabled for an organization; other modules use it to gate them. */
public interface OrganizationFeatures {

	boolean isEnabled(UUID organizationId, Feature feature);

	Set<Feature> enabledFor(UUID organizationId);
}
