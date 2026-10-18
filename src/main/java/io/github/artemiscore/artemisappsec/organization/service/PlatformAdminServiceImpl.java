package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Organization;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationFeatureStore;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationRepository;

@Service
@Transactional(readOnly = true)
class PlatformAdminServiceImpl implements PlatformAdminService, OrganizationFeatures {

	private final OrganizationRepository organizations;
	private final MemberRepository members;
	private final OrganizationFeatureStore features;
	private final RepositoryCounts repositories;

	PlatformAdminServiceImpl(OrganizationRepository organizations, MemberRepository members, OrganizationFeatureStore features,
			RepositoryCounts repositories) {
		this.organizations = organizations;
		this.members = members;
		this.features = features;
		this.repositories = repositories;
	}

	@Override
	public List<AdminOrganizationView> organizations() {
		List<Organization> all = organizations.findAll();
		Map<UUID, List<Member>> membersByOrg = members.findAll().stream()
			.filter(Member::isActive)
			.collect(Collectors.groupingBy(Member::getOrganizationId));
		Map<UUID, Set<Feature>> enabled = features.featuresOf(all.stream().map(Organization::getId).toList());
		Map<UUID, Long> repositoryCounts = repositories.repositoriesByOrganization();
		return all.stream()
			.map(o -> toView(o, membersByOrg.getOrDefault(o.getId(), List.of()), enabled.getOrDefault(o.getId(), Set.of()),
					repositoryCounts.getOrDefault(o.getId(), 0L)))
			.sorted(Comparator.comparing(AdminOrganizationView::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	@Override
	@Transactional
	public AdminOrganizationView updateFeatures(String slug, Set<Feature> requested, String admin) {
		Organization organization = organizations.findBySlug(slug).orElseThrow(MembershipServiceImpl::organizationNotFound);
		features.replace(organization.getId(), requested.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(requested), admin);
		return toView(organization, members.findByOrganizationIdOrderByCreatedAtAsc(organization.getId()).stream()
			.filter(Member::isActive).toList(), enabledFor(organization.getId()),
				repositories.repositoriesByOrganization().getOrDefault(organization.getId(), 0L));
	}

	@Override
	public boolean isEnabled(UUID organizationId, Feature feature) {
		return features.isEnabled(organizationId, feature);
	}

	@Override
	public Set<Feature> enabledFor(UUID organizationId) {
		return features.featuresOf(List.of(organizationId)).getOrDefault(organizationId, Set.of());
	}

	private static AdminOrganizationView toView(Organization organization, List<Member> active, Set<Feature> enabled,
			long repositoryCount) {
		String owner = active.stream().filter(m -> m.getRole() == Role.OWNER).map(Member::getName).findFirst().orElse(null);
		return new AdminOrganizationView(organization.getSlug(), organization.getName(), organization.getCreatedAt(), owner,
				active.size(), repositoryCount, enabled.stream().sorted().toList());
	}
}
