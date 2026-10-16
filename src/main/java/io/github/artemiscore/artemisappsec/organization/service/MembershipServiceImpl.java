package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Integration;
import io.github.artemiscore.artemisappsec.organization.domain.Invitation;
import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Organization;
import io.github.artemiscore.artemisappsec.organization.domain.OrganizationSettings;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.IntegrationRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.InvitationRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationSettingsRepository;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

@Service
@Transactional
class MembershipServiceImpl implements MembershipService {

	static final int MAX_OWNED_ORGANIZATIONS = 5;
	private static final String WEB_CLIENT_ID = "artemisappsec-web";

	private final OrganizationRepository organizations;
	private final MemberRepository members;
	private final InvitationRepository invitations;
	private final OrganizationSettingsRepository settings;
	private final IntegrationRepository integrations;
	private final PlanLookup plans;
	private final ApplicationEventPublisher events;
	private final String issuer;

	MembershipServiceImpl(OrganizationRepository organizations, MemberRepository members, InvitationRepository invitations,
			OrganizationSettingsRepository settings, IntegrationRepository integrations, PlanLookup plans,
			ApplicationEventPublisher events, ArtemisAppSecProperties properties) {
		this.organizations = organizations;
		this.members = members;
		this.invitations = invitations;
		this.settings = settings;
		this.integrations = integrations;
		this.plans = plans;
		this.events = events;
		this.issuer = properties.security().oidc().issuerUri();
	}

	@Override
	public Tenant resolve(String slug, Identity identity) {
		Organization organization = organizations.findBySlug(slug).orElseThrow(MembershipServiceImpl::organizationNotFound);
		Member member = findMember(organization.getId(), identity)
			.filter(Member::isActive)
			.orElseThrow(MembershipServiceImpl::organizationNotFound);
		member.seen(Instant.now());
		return new Tenant(organization.getId(), organization.getSlug(), member.getId(), member.getRole(), identity.username());
	}

	@Override
	@Transactional(readOnly = true)
	public Tenant resolveUnauthenticated(String slug) {
		Organization organization = organizations.findBySlug(slug).orElseThrow(MembershipServiceImpl::organizationNotFound);
		return new Tenant(organization.getId(), organization.getSlug(), null, Role.OWNER, "local-developer");
	}

	@Override
	public List<OrganizationMembership> organizationsOf(Identity identity) {
		List<Member> found = members.findForAccount(identity.subject(), identity.matchingEmail());
		Set<UUID> linked = found.stream()
			.filter(m -> identity.subject().equals(m.getUserSubject()))
			.map(Member::getOrganizationId)
			.collect(Collectors.toSet());
		List<Member> memberships = new ArrayList<>();
		for (Member member : found) {
			if (member.getUserSubject() == null) {
				if (linked.contains(member.getOrganizationId())) {
					continue;
				}
				member.bind(identity.subject(), identity.name());
			}
			if (member.isActive()) {
				memberships.add(member);
			}
		}
		Map<UUID, Organization> byId = organizations.findAllById(memberships.stream().map(Member::getOrganizationId).toList())
			.stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));
		Map<UUID, String> planCodes = plans.plansOf(byId.keySet());
		return memberships.stream()
			.map(m -> toMembership(byId.get(m.getOrganizationId()), m.getRole(), planCodes))
			.sorted(Comparator.comparing(OrganizationMembership::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	@Override
	public OrganizationMembership create(CreateOrganizationCommand command, Identity identity) {
		if (members.countByUserSubjectAndRole(identity.subject(), Role.OWNER) >= MAX_OWNED_ORGANIZATIONS) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "organization-limit",
					"You can own at most %d organizations".formatted(MAX_OWNED_ORGANIZATIONS));
		}
		Slugs.requireAvailable(command.slug(), organizations);
		Organization organization = organizations.save(new Organization(command.slug(), command.name().strip()));
		String language = command.defaultLanguage() == null ? "en-US" : command.defaultLanguage();
		settings.save(new OrganizationSettings(organization.getId(), language, issuer, WEB_CLIENT_ID));
		integrations.saveAll(Integration.catalogFor(organization.getId()));
		members.save(new Member(organization.getId(), identity.subject(), identity.displayName(), identity.email(), Role.OWNER));
		events.publishEvent(new OrganizationCreated(organization.getId()));
		return toMembership(organization, Role.OWNER, plans.plansOf(List.of(organization.getId())));
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendingInvitationView> pendingInvitations(Identity identity) {
		if (identity.matchingEmail() == null) {
			return List.of();
		}
		List<Invitation> pending = invitations.findByEmailAndExpiresAtAfterOrderBySentAtDesc(identity.matchingEmail(), Instant.now());
		Map<UUID, Organization> byId = organizations.findAllById(pending.stream().map(Invitation::getOrganizationId).toList())
			.stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));
		return pending.stream().map(i -> {
			Organization o = byId.get(i.getOrganizationId());
			return new PendingInvitationView(i.getId(), new PendingInvitationView.Organization(o.getSlug(), o.getName()),
					i.getRole(), i.getInvitedBy(), i.getSentAt(), i.getExpiresAt());
		}).toList();
	}

	@Override
	public OrganizationMembership acceptInvitation(UUID invitationId, Identity identity) {
		Invitation invitation = invitationFor(invitationId, identity);
		UUID organizationId = invitation.getOrganizationId();
		Optional<Member> existing = members.findByOrganizationIdAndUserSubject(organizationId, identity.subject())
			.or(() -> members.findByOrganizationIdAndEmail(organizationId, identity.email()));
		Member member;
		if (existing.isPresent() && existing.get().isActive()) {
			member = existing.get();
		}
		else if (existing.isPresent()) {
			member = existing.get();
			member.rejoin(identity.subject(), invitation.getRole());
		}
		else {
			member = members.save(new Member(organizationId, identity.subject(), identity.displayName(), identity.email(),
					invitation.getRole()));
		}
		invitations.delete(invitation);
		Organization organization = organizations.findById(organizationId).orElseThrow();
		return toMembership(organization, member.getRole(), plans.plansOf(List.of(organizationId)));
	}

	@Override
	public void declineInvitation(UUID invitationId, Identity identity) {
		invitations.delete(invitationFor(invitationId, identity));
	}

	@Override
	public void leave(String slug, Identity identity) {
		Organization organization = organizations.findBySlug(slug).orElseThrow(MembershipServiceImpl::organizationNotFound);
		Member member = findMember(organization.getId(), identity).orElseThrow(MembershipServiceImpl::organizationNotFound);
		if (member.getRole() == Role.OWNER) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "owner-cannot-leave",
					"Transfer the ownership to another member before leaving, or delete the organization");
		}
		members.delete(member);
	}

	/** By subject; members created before the account existed (invitations, seeds) are linked by verified e-mail. */
	private Optional<Member> findMember(UUID organizationId, Identity identity) {
		Optional<Member> bySubject = members.findByOrganizationIdAndUserSubject(organizationId, identity.subject());
		if (bySubject.isPresent() || identity.matchingEmail() == null) {
			return bySubject;
		}
		Optional<Member> byEmail = members.findByOrganizationIdAndEmail(organizationId, identity.matchingEmail())
			.filter(m -> m.getUserSubject() == null);
		byEmail.ifPresent(m -> m.bind(identity.subject(), identity.name()));
		return byEmail;
	}

	private Invitation invitationFor(UUID invitationId, Identity identity) {
		return invitations.findById(invitationId)
			.filter(i -> identity.matchingEmail() != null && i.getEmail().equals(identity.matchingEmail()))
			.filter(i -> !i.isExpired(Instant.now()))
			.orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "invitation-not-found",
					"Invitation '%s' not found".formatted(invitationId)));
	}

	static ApiProblemException organizationNotFound() {
		return new ApiProblemException(HttpStatus.NOT_FOUND, "organization-not-found", "Organization not found");
	}

	private static OrganizationMembership toMembership(Organization organization, Role role, Map<UUID, String> planCodes) {
		return new OrganizationMembership(organization.getSlug(), organization.getName(), role,
				role.permissions().stream().sorted().toList(), planCodes.getOrDefault(organization.getId(), "free"));
	}
}
