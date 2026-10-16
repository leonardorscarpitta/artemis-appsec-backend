package io.github.artemiscore.artemisappsec.organization.service;

public interface OrganizationSettingsService {

	OrganizationSettingsView get();

	OrganizationSettingsView update(OrganizationSettingsView settings);

	void deleteOrganization();
}
