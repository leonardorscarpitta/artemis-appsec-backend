package io.github.artemiscore.artemis.organization.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.organization.domain.OrganizationSettings;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, Short> {
}
