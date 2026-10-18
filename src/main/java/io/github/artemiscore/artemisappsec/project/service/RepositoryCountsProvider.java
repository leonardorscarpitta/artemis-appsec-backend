package io.github.artemiscore.artemisappsec.project.service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.service.RepositoryCounts;
import io.github.artemiscore.artemisappsec.project.persistence.ProjectRepository;

@Component
@Transactional(readOnly = true)
class RepositoryCountsProvider implements RepositoryCounts {

	private final ProjectRepository projects;

	RepositoryCountsProvider(ProjectRepository projects) {
		this.projects = projects;
	}

	@Override
	public Map<UUID, Long> repositoriesByOrganization() {
		return projects.countActiveByOrganization().stream()
			.collect(Collectors.toMap(ProjectRepository.OrganizationCount::getOrganizationId,
					ProjectRepository.OrganizationCount::getTotal));
	}
}
