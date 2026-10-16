package io.github.artemiscore.artemis.organization.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemis.organization.domain.Invitation;
import io.github.artemiscore.artemis.organization.domain.Member;
import io.github.artemiscore.artemis.organization.domain.Role;
import io.github.artemiscore.artemis.organization.persistence.InvitationRepository;
import io.github.artemiscore.artemis.organization.persistence.MemberRepository;
import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import io.github.artemiscore.artemis.shared.error.ResourceNotFoundException;

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
	public List<MemberView> findMembers() {
		return members.findAllByOrderByCreatedAtAsc().stream().map(TeamServiceImpl::toView).toList();
	}

	@Override
	@Transactional
	public MemberView changeRole(UUID memberId, Role role) {
		Member member = members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
		member.changeRole(role);
		return toView(member);
	}

	@Override
	public List<InvitationView> findInvitations() {
		return invitations.findAllByOrderBySentAtDesc().stream().map(TeamServiceImpl::toView).toList();
	}

	@Override
	@Transactional
	public InvitationView invite(InviteCommand command) {
		String email = command.email().strip().toLowerCase(Locale.ROOT);
		if (members.existsByEmailIgnoreCase(email)) {
			throw new BusinessRuleException("'%s' is already a member".formatted(email));
		}
		if (invitations.existsByEmailIgnoreCase(email)) {
			throw new BusinessRuleException("'%s' already has a pending invitation".formatted(email));
		}
		// Delivery of the invitation e-mail belongs to the notification integration (not implemented yet).
		return toView(invitations.save(new Invitation(email, command.role())));
	}

	@Override
	@Transactional
	public InvitationView resend(UUID invitationId) {
		Invitation invitation = loadInvitation(invitationId);
		invitation.resend();
		return toView(invitation);
	}

	@Override
	@Transactional
	public void revokeInvitation(UUID invitationId) {
		invitations.delete(loadInvitation(invitationId));
	}

	@Override
	public List<RoleView> roles() {
		return Arrays.stream(Role.values())
			.map(role -> new RoleView(role, role.permissions().stream().sorted().toList(), role != Role.OWNER))
			.toList();
	}

	private Invitation loadInvitation(UUID id) {
		return invitations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invitation", id));
	}

	private static MemberView toView(Member m) {
		return new MemberView(m.getId(), m.getName(), m.getEmail(), m.getRole(), m.isActive(), m.getLastActiveAt());
	}

	private static InvitationView toView(Invitation i) {
		return new InvitationView(i.getId(), i.getEmail(), i.getRole(), i.getSentAt());
	}
}
