package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Organization;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class PlatformUserServiceImpl implements PlatformUserService {

	private static final Logger log = LoggerFactory.getLogger(PlatformUserServiceImpl.class);

	private final KeycloakAdminClient keycloak;
	private final MemberRepository members;
	private final OrganizationRepository organizations;
	private final DeletedAccounts deletedAccounts;

	PlatformUserServiceImpl(KeycloakAdminClient keycloak, MemberRepository members, OrganizationRepository organizations,
			DeletedAccounts deletedAccounts) {
		this.keycloak = keycloak;
		this.members = members;
		this.organizations = organizations;
		this.deletedAccounts = deletedAccounts;
	}

	@Override
	public AdminUserPage users(String search, int first, int max) {
		String query = search == null ? null : search.strip();
		List<KeycloakAdminClient.KeycloakUser> users = keycloak.users(query, Math.max(first, 0),
				Math.clamp(max, 1, MAX_PAGE_SIZE));
		long total = keycloak.countUsers(query);
		Map<String, List<AdminUserView.Membership>> memberships = memberships(
				users.stream().map(KeycloakAdminClient.KeycloakUser::id).toList());
		List<AdminUserView> views = users.stream()
			.map(u -> toView(u, keycloak.isPlatformAdmin(u.id()), memberships.getOrDefault(u.id(), List.of())))
			.toList();
		return new AdminUserPage(views, total);
	}

	@Override
	@Transactional
	public void deleteUser(String id, String adminSubject, String admin) {
		KeycloakAdminClient.KeycloakUser user = keycloak.user(id)
			.orElseThrow(() -> new ResourceNotFoundException("User", id));
		if (id.equals(adminSubject)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "cannot-delete-self", "You cannot delete your own account here");
		}
		if (keycloak.isPlatformAdmin(id)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "platform-admin",
					"Administrators cannot be deleted; remove the artemis-admin role in Keycloak first");
		}
		List<String> owned = memberships(List.of(id)).getOrDefault(id, List.of()).stream()
			.filter(m -> m.role() == Role.OWNER)
			.map(AdminUserView.Membership::slug)
			.toList();
		if (!owned.isEmpty()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "owns-organizations",
					"The user owns organizations; delete them or transfer their ownership first",
					Map.of("organizations", owned));
		}
		members.deleteByUserSubject(id);
		// Last, so a failure here rolls the membership removal back.
		keycloak.deleteUser(id);
		deletedAccounts.add(id);
		log.info("Artemis administrator {} deleted user {} ({})", admin, id, user.username());
	}

	/** Active memberships by Keycloak user id, sorted by organization name. */
	private Map<String, List<AdminUserView.Membership>> memberships(List<String> subjects) {
		if (subjects.isEmpty()) {
			return Map.of();
		}
		List<Member> rows = members.findByUserSubjectIn(subjects).stream().filter(Member::isActive).toList();
		Map<UUID, Organization> byId = organizations.findAllById(rows.stream().map(Member::getOrganizationId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));
		return rows.stream()
			.filter(m -> byId.containsKey(m.getOrganizationId()))
			.collect(Collectors.groupingBy(Member::getUserSubject, Collectors.mapping(m -> {
				Organization o = byId.get(m.getOrganizationId());
				return new AdminUserView.Membership(o.getSlug(), o.getName(), m.getRole());
			}, Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
				.sorted(Comparator.comparing(AdminUserView.Membership::name, String.CASE_INSENSITIVE_ORDER))
				.toList()))));
	}

	private static AdminUserView toView(KeycloakAdminClient.KeycloakUser user, boolean platformAdmin,
			List<AdminUserView.Membership> organizations) {
		String name = String.join(" ", List.of(nullToEmpty(user.firstName()), nullToEmpty(user.lastName()))).strip();
		Instant createdAt = user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp());
		return new AdminUserView(user.id(), user.username(), name.isEmpty() ? null : name, user.email(), createdAt,
				user.enabled(), platformAdmin, organizations);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value.strip();
	}
}
