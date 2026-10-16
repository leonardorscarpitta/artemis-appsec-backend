package io.github.artemiscore.artemis.organization.service;

public interface OrganizationSettingsService {

	OrganizationSettingsView get();

	OrganizationSettingsView update(OrganizationSettingsView settings);

	void deleteOrganization();
}
