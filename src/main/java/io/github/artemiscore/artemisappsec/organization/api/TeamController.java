package io.github.artemiscore.artemisappsec.organization.api;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.service.InvitationView;
import io.github.artemiscore.artemisappsec.organization.service.InviteCommand;
import io.github.artemiscore.artemisappsec.organization.service.MemberView;
import io.github.artemiscore.artemisappsec.organization.service.RoleView;
import io.github.artemiscore.artemisappsec.organization.service.TeamService;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Team", description = "Members, invitations, roles and permissions")
class TeamController {

	record ChangeRoleRequest(@NotNull Role role) {
	}

	record TransferOwnershipRequest(@NotNull UUID memberId) {
	}

	record RolePermissionsRequest(@NotNull Set<@NotNull Permission> permissions) {
	}

	private final TeamService team;

	TeamController(TeamService team) {
		this.team = team;
	}

	/** E-mail addresses are personal data: only roles that manage the team see them. */
	@GetMapping("/members")
	List<MemberView> members(Tenant tenant) {
		List<MemberView> members = team.findMembers(tenant.organizationId());
		return tenant.can(Permission.TEAM) ? members
				: members.stream().map(MemberView::withoutEmail).toList();
	}

	@PatchMapping("/members/{id}")
	@RequiresPermission(Permission.TEAM)
	MemberView changeRole(Tenant tenant, @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
		return team.changeRole(tenant, id, request.role());
	}

	@DeleteMapping("/members/{id}")
	@RequiresPermission(Permission.TEAM)
	ResponseEntity<Void> remove(Tenant tenant, @PathVariable UUID id) {
		team.removeMember(tenant, id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/members/transfer-ownership")
	@RequiresPermission(Permission.BILLING)
	List<MemberView> transferOwnership(Tenant tenant, @Valid @RequestBody TransferOwnershipRequest request) {
		return team.transferOwnership(tenant, request.memberId());
	}

	@GetMapping("/invitations")
	@RequiresPermission(Permission.TEAM)
	List<InvitationView> invitations(Tenant tenant) {
		return team.findInvitations(tenant.organizationId());
	}

	@PostMapping("/invitations")
	@RequiresPermission(Permission.TEAM)
	ResponseEntity<InvitationView> invite(Tenant tenant, @Valid @RequestBody InviteCommand command) {
		InvitationView invitation = team.invite(tenant, command);
		return ResponseEntity.created(URI.create("/api/v1/invitations/" + invitation.id())).body(invitation);
	}

	@PostMapping("/invitations/{id}/resend")
	@RequiresPermission(Permission.TEAM)
	InvitationView resend(Tenant tenant, @PathVariable UUID id) {
		return team.resend(tenant.organizationId(), id);
	}

	@DeleteMapping("/invitations/{id}")
	@RequiresPermission(Permission.TEAM)
	ResponseEntity<Void> revoke(Tenant tenant, @PathVariable UUID id) {
		team.revokeInvitation(tenant.organizationId(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/roles")
	List<RoleView> roles(Tenant tenant) {
		return team.roles(tenant);
	}

	/** Owners and admins only (checked by role in the service); every role keeps viewFindings, billing is owner-only. */
	@PutMapping("/roles/{role}/permissions")
	@RequiresPermission(Permission.VIEW_FINDINGS)
	RoleView updateRolePermissions(Tenant tenant, @PathVariable Role role, @Valid @RequestBody RolePermissionsRequest request) {
		return team.updateRolePermissions(tenant, role, request.permissions());
	}
}
