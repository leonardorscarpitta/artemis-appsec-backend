package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.Set;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

/**
 * For Artemis administrators (realm role {@code artemis-admin}): every organization's metadata, its optional
 * features and deletion. Grants no membership; organizations' content is viewed read-only through the regular
 * organization-scoped API (see {@link MembershipService#resolve}).
 */
public interface PlatformAdminService {

	List<AdminOrganizationView> organizations();

	AdminOrganizationView organization(String slug);

	/** Deletes the organization and everything linked to it, like its owner's "Delete organization". */
	void deleteOrganization(String slug, String admin);

	AdminOrganizationView updateFeatures(String slug, Set<Feature> features, String admin);
}
