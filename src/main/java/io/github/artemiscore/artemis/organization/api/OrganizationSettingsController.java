package io.github.artemiscore.artemis.organization.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.organization.service.OrganizationSettingsService;
import io.github.artemiscore.artemis.organization.service.OrganizationSettingsView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "Organization settings")
class OrganizationSettingsController {

	private final OrganizationSettingsService settings;

	OrganizationSettingsController(OrganizationSettingsService settings) {
		this.settings = settings;
	}

	@GetMapping("/settings")
	OrganizationSettingsView get() {
		return settings.get();
	}

	@PutMapping("/settings")
	OrganizationSettingsView update(@Valid @RequestBody OrganizationSettingsView request) {
		return settings.update(request);
	}

	@DeleteMapping
	void delete() {
		settings.deleteOrganization();
	}
}
