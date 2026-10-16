package io.github.artemiscore.artemisappsec.organization.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationSettingsService;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationSettingsView;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "Settings of the request's organization")
class OrganizationSettingsController {

	private final OrganizationSettingsService settings;

	OrganizationSettingsController(OrganizationSettingsService settings) {
		this.settings = settings;
	}

	@GetMapping("/settings")
	OrganizationSettingsView get(Tenant tenant) {
		return settings.get(tenant.organizationId());
	}

	@PutMapping("/settings")
	@RequiresPermission(Permission.TEAM)
	OrganizationSettingsView update(Tenant tenant, @Valid @RequestBody OrganizationSettingsView request) {
		return settings.update(tenant.organizationId(), request);
	}

	/** Owner only: deletes the organization with all its repositories, findings, runs, reports and members. */
	@DeleteMapping
	@RequiresPermission(Permission.BILLING)
	ResponseEntity<Void> delete(Tenant tenant) {
		settings.deleteOrganization(tenant.organizationId());
		return ResponseEntity.noContent().build();
	}
}
