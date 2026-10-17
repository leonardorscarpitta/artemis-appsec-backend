package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Invitation;
import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.InvitationRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class TeamServiceImpl implements TeamService {

	private final MemberRepository members;
	private final InvitationRepository invitations;

	TeamServiceImpl(MemberRepository members, InvitationRepository invitations) {
		this.members = members;
		this.invitations = invitations;
	}

	@Override
	public List<MemberView> findMembers(UUID organizationId) {
		return members.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream().map(TeamServiceImpl::toView).toList();
	}

	@Override
	@Transactional
	public MemberView changeRole(Tenant tenant, UUID memberId, Role role) {
		Member member = loadOther(tenant, memberId, "change your own role");
		requireOwnerForAdmins(tenant, member.getRole());
		requireOwnerForAdmins(tenant, role);
		member.changeRole(role);
		return toView(member);
	}

	@Override
	@Transactional
	public void removeMember(Tenant tenant, UUID memberId) {
		Member member = loadOther(tenant, memberId, "remove yourself; leave the organization instead");
		if (member.getRole() == Role.OWNER) {
			throw new BusinessRuleException("The owner cannot be removed");
		}
		requireOwnerForAdmins(tenant, member.getRole());
		members.delete(member);
	}

	@Override
	@Transactional
	public List<MemberView> transferOwnership(Tenant tenant, UUID memberId) {
		if (tenant.role() != Role.OWNER || tenant.memberId() == null) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "Only the owner can transfer the ownership");
		}
		Member target = loadOther(tenant, memberId, "transfer the ownership to yourself");
		if (!target.isActive()) {
			throw new BusinessRuleException("The new owner must be an active member");
		}
		Member owner = load(tenant.organizationId(), tenant.memberId());
		owner.stepDownAsOwner();
		members.flush();
		target.becomeOwner();
		return findMembers(tenant.organizationId());
	}

	@Override
	public List<InvitationView> findInvitations(UUID organizationId) {
		Instant now = Instant.now();
		return invitations.findByOrganizationIdOrderBySentAtDesc(organizationId).stream().map(i -> toView(i, now)).toList();
	}

	@Override
	@Transactional
	public InvitationView invite(Tenant tenant, InviteCommand command) {
		requireOwnerForAdmins(tenant, command.role());
		String email = command.email().strip().toLowerCase(Locale.ROOT);
		if (members.findByOrganizationIdAndEmail(tenant.organizationId(), email).filter(Member::isActive).isPresent()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "already-member", "'%s' is already a member".formatted(email));
		}
		Optional<Invitation> previous = invitations.findByOrganizationIdAndEmail(tenant.organizationId(), email);
		if (previous.isPresent() && !previous.get().isExpired(Instant.now())) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "already-invited",
					"'%s' already has a pending invitation".formatted(email));
		}
		previous.ifPresent(i -> {
			invitations.delete(i);
			invitations.flush();
		});
		// Invitations are not e-mailed yet: the invitee sees them after signing in with this address.
		Invitation invitation = new Invitation(tenant.organizationId(), email, command.role(), inviterName(tenant));
		// save() merges (the id is pre-assigned) and returns a copy without the raw link token, so answer from this one.
		invitations.save(invitation);
		return toView(invitation, Instant.now());
	}

	@Override
	@Transactional
	public InvitationView resend(UUID organizationId, UUID invitationId) {
		Invitation invitation = loadInvitation(organizationId, invitationId);
		invitation.resend();
		return toView(invitation, Instant.now());
	}

	@Override
	@Transactional
	public void revokeInvitation(UUID organizationId, UUID invitationId) {
		invitations.delete(loadInvitation(organizationId, invitationId));
	}

	@Override
	public List<RoleView> roles() {
		return Arrays.stream(Role.values())
			.map(role -> new RoleView(role, role.permissions().stream().sorted().toList(), role != Role.OWNER))
			.toList();
	}

	private String inviterName(Tenant tenant) {
		return tenant.memberId() == null ? tenant.actor()
				: members.findById(tenant.memberId()).map(Member::getName).orElse(tenant.actor());
	}

	/** Only the owner manages admins: admins cannot promote, demote, invite or remove admins. */
	private static void requireOwnerForAdmins(Tenant tenant, Role role) {
		if (role == Role.ADMIN && tenant.role() != Role.OWNER) {
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "owner-only", "Only the owner can manage admins");
		}
	}

	private Member loadOther(Tenant tenant, UUID memberId, String forbiddenOnSelf) {
		if (memberId.equals(tenant.memberId())) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "self-change", "You cannot " + forbiddenOnSelf);
		}
		return load(tenant.organizationId(), memberId);
	}

	private Member load(UUID organizationId, UUID memberId) {
		return members.findByIdAndOrganizationId(memberId, organizationId)
			.orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
	}

	private Invitation loadInvitation(UUID organizationId, UUID id) {
		return invitations.findByIdAndOrganizationId(id, organizationId)
			.orElseThrow(() -> new ResourceNotFoundException("Invitation", id));
	}

	private static MemberView toView(Member m) {
		return new MemberView(m.getId(), m.getName(), m.getEmail(), m.getRole(), m.isActive(), m.getLastActiveAt());
	}

	private static InvitationView toView(Invitation i, Instant now) {
		return new InvitationView(i.getId(), i.getEmail(), i.getRole(), i.getInvitedBy(), i.getSentAt(), i.getExpiresAt(),
				i.isExpired(now), i.getIssuedToken());
	}
}
