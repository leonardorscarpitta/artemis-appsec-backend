package io.github.artemiscore.artemis.organization.service;

import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemis.organization.domain.Role;

public interface TeamService {

	List<MemberView> findMembers();

	MemberView changeRole(UUID memberId, Role role);

	List<InvitationView> findInvitations();

	InvitationView invite(InviteCommand command);

	InvitationView resend(UUID invitationId);

	void revokeInvitation(UUID invitationId);

	List<RoleView> roles();
}
