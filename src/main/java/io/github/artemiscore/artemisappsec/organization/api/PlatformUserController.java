package io.github.artemiscore.artemisappsec.organization.api;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.service.AdminUserPage;
import io.github.artemiscore.artemisappsec.organization.service.Identity;
import io.github.artemiscore.artemisappsec.organization.service.PlatformUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requires the {@code artemis-admin} realm role (see {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/admin/users")
@WithoutOrganization
@Tag(name = "Platform administration", description = "Accounts with their organizations and roles, and account deletion (Artemis administrators only)")
class PlatformUserController {

	private final PlatformUserService users;

	PlatformUserController(PlatformUserService users) {
		this.users = users;
	}

	/** Searches username, name and e-mail; {@code max} is capped at {@value PlatformUserService#MAX_PAGE_SIZE}. */
	@GetMapping
	AdminUserPage users(@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") int first, @RequestParam(defaultValue = "100") int max) {
		return users.users(search, first, max);
	}

	/** Deletes the Keycloak account (and its sessions) and its memberships; 409 for yourself, administrators and owners. */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String id, Principal principal) {
		Identity caller = Identities.current();
		users.deleteUser(id, caller == null ? null : caller.subject(), principal == null ? "local-developer" : principal.getName());
		return ResponseEntity.noContent().build();
	}
}
