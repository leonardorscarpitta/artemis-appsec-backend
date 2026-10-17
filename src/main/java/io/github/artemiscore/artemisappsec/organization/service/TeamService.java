package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.domain.Role;

public interface TeamService {

	List<MemberView> findMembers(UUID organizationId);

	MemberView changeRole(Tenant tenant, UUID memberId, Role role);

	void removeMember(Tenant tenant, UUID memberId);

	/** The current owner becomes an admin. */
	List<MemberView> transferOwnership(Tenant tenant, UUID memberId);

	List<InvitationView> findInvitations(UUID organizationId);

	InvitationView invite(Tenant tenant, InviteCommand command);

	InvitationView resend(UUID organizationId, UUID invitationId);

	void revokeInvitation(UUID organizationId, UUID invitationId);

	List<RoleView> roles(Tenant tenant);

	/** Owners edit every role but their own; admins only analysts, developers and viewers. */
	RoleView updateRolePermissions(Tenant tenant, Role role, Set<Permission> permissions);
}
