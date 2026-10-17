package io.github.artemiscore.artemisappsec.organization.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.service.CreateOrganizationCommand;
import io.github.artemiscore.artemisappsec.organization.service.Identity;
import io.github.artemiscore.artemisappsec.organization.service.MembershipService;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationMembership;
import io.github.artemiscore.artemisappsec.organization.service.PendingInvitationView;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@WithoutOrganization
@Tag(name = "Memberships", description = "Organizations of the signed-in account and invitations addressed to it")
class MembershipController {

	private final MembershipService memberships;

	MembershipController(MembershipService memberships) {
		this.memberships = memberships;
	}

	@GetMapping("/me/organizations")
	List<OrganizationMembership> organizations(@AuthenticationPrincipal Jwt jwt) {
		return memberships.organizationsOf(identity(jwt));
	}

	@PostMapping("/organizations")
	ResponseEntity<OrganizationMembership> create(@Valid @RequestBody CreateOrganizationCommand command,
			@AuthenticationPrincipal Jwt jwt) {
		OrganizationMembership created = memberships.create(command, identity(jwt));
		return ResponseEntity.created(URI.create("/api/v1/me/organizations/" + created.slug())).body(created);
	}

	@PostMapping("/me/organizations/{slug}/leave")
	ResponseEntity<Void> leave(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
		memberships.leave(slug, identity(jwt));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me/invitations")
	List<PendingInvitationView> invitations(@AuthenticationPrincipal Jwt jwt) {
		return memberships.pendingInvitations(identity(jwt));
	}

	@PostMapping("/me/invitations/{id}/accept")
	OrganizationMembership accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		return memberships.acceptInvitation(id, identity(jwt));
	}

	/** One-time invitation link (works for any signed-in account; the token is consumed). */
	@PostMapping("/me/invitations/accept")
	OrganizationMembership acceptLink(@Valid @RequestBody AcceptInvitationRequest request, @AuthenticationPrincipal Jwt jwt) {
		return memberships.acceptInvitationLink(request.token(), identity(jwt));
	}

	record AcceptInvitationRequest(@NotBlank @Size(max = 100) String token) {
	}

	@PostMapping("/me/invitations/{id}/decline")
	ResponseEntity<Void> decline(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		memberships.declineInvitation(id, identity(jwt));
		return ResponseEntity.noContent().build();
	}

	private static Identity identity(Jwt jwt) {
		if (jwt == null) {
			throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "unauthenticated",
					"Organizations require a signed-in account (authentication is disabled)");
		}
		return Identities.from(jwt);
	}
}
