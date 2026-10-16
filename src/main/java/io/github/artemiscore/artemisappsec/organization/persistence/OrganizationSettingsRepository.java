package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.OrganizationSettings;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {
}
