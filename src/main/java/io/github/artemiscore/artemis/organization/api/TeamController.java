package io.github.artemiscore.artemis.organization.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.organization.domain.Role;
import io.github.artemiscore.artemis.organization.service.InvitationView;
import io.github.artemiscore.artemis.organization.service.InviteCommand;
import io.github.artemiscore.artemis.organization.service.MemberView;
import io.github.artemiscore.artemis.organization.service.RoleView;
import io.github.artemiscore.artemis.organization.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Team", description = "Members, invitations, roles and permissions")
class TeamController {

	record ChangeRoleRequest(@NotNull Role role) {
	}

	private final TeamService team;

	TeamController(TeamService team) {
		this.team = team;
	}

	@GetMapping("/members")
	List<MemberView> members() {
		return team.findMembers();
	}

	@PatchMapping("/members/{id}")
	MemberView changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
		return team.changeRole(id, request.role());
	}

	@GetMapping("/invitations")
	List<InvitationView> invitations() {
		return team.findInvitations();
	}

	@PostMapping("/invitations")
	ResponseEntity<InvitationView> invite(@Valid @RequestBody InviteCommand command) {
		InvitationView invitation = team.invite(command);
		return ResponseEntity.created(URI.create("/api/v1/invitations/" + invitation.id())).body(invitation);
	}

	@PostMapping("/invitations/{id}/resend")
	InvitationView resend(@PathVariable UUID id) {
		return team.resend(id);
	}

	@DeleteMapping("/invitations/{id}")
	ResponseEntity<Void> revoke(@PathVariable UUID id) {
		team.revokeInvitation(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/roles")
	List<RoleView> roles() {
		return team.roles();
	}
}
