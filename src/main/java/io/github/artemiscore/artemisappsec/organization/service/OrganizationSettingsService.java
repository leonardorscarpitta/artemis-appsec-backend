package io.github.artemiscore.artemisappsec.organization.service;

import java.util.UUID;

public interface OrganizationSettingsService {

	OrganizationSettingsView get(UUID organizationId);

	OrganizationSettingsView update(UUID organizationId, OrganizationSettingsView settings);

	/** Permanently deletes the organization and everything in it. */
	void deleteOrganization(UUID organizationId);
}
