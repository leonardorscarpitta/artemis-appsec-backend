package io.github.artemiscore.artemisappsec.organization.api;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.service.AdminOrganizationView;
import io.github.artemiscore.artemisappsec.organization.service.PlatformAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Requires the {@code artemis-admin} realm role (see {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@WithoutOrganization
@Tag(name = "Platform administration", description = "Organizations' metadata, optional features and deletion (Artemis administrators only)")
class PlatformAdminController {

	private final PlatformAdminService admin;

	PlatformAdminController(PlatformAdminService admin) {
		this.admin = admin;
	}

	@GetMapping
	List<AdminOrganizationView> organizations() {
		return admin.organizations();
	}

	@GetMapping("/{slug}")
	AdminOrganizationView organization(@PathVariable String slug) {
		return admin.organization(slug);
	}

	/** Deletes the organization with all its repositories, findings, runs, reports, members and settings. */
	@DeleteMapping("/{slug}")
	ResponseEntity<Void> delete(@PathVariable String slug, Principal principal) {
		admin.deleteOrganization(slug, actor(principal));
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{slug}/features")
	AdminOrganizationView updateFeatures(@PathVariable String slug, @Valid @RequestBody FeaturesRequest request,
			Principal principal) {
		return admin.updateFeatures(slug, request.features(), actor(principal));
	}

	private static String actor(Principal principal) {
		return principal == null ? "local-developer" : principal.getName();
	}

	record FeaturesRequest(@NotNull Set<@NotNull Feature> features) {
	}
}
