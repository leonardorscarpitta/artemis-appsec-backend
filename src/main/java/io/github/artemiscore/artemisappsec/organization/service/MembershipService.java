package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.UUID;

/** Organizations of the signed-in account: tenant resolution, creation, invitations addressed to it, leaving. */
public interface MembershipService {

	/** Resolves the organization a request targets; unknown slugs and non-members get the same 404. */
	Tenant resolve(String slug, Identity identity);

	/** Local development without authentication: full access to any existing organization. */
	Tenant resolveUnauthenticated(String slug);

	List<OrganizationMembership> organizationsOf(Identity identity);

	OrganizationMembership create(CreateOrganizationCommand command, Identity identity);

	List<PendingInvitationView> pendingInvitations(Identity identity);

	OrganizationMembership acceptInvitation(UUID invitationId, Identity identity);

	void declineInvitation(UUID invitationId, Identity identity);

	/** Joins through a one-time invitation link; the invitation is consumed. */
	OrganizationMembership acceptInvitationLink(String token, Identity identity);

	void leave(String slug, Identity identity);
}
