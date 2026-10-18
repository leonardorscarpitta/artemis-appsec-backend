package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.Set;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

/**
 * For Artemis administrators (realm role {@code artemis-admin}): every organization's metadata and its optional
 * features. Never exposes content (repositories, findings, runs) or member e-mails, and grants no membership.
 */
public interface PlatformAdminService {

	List<AdminOrganizationView> organizations();

	AdminOrganizationView updateFeatures(String slug, Set<Feature> features, String admin);
}
