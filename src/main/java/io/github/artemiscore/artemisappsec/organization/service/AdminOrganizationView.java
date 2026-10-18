package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

public record AdminOrganizationView(String slug, String name, Instant createdAt, String ownerName, long memberCount,
		long repositoryCount, List<Feature> features) {
}
