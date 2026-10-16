package io.github.artemiscore.artemisappsec.finding.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.persistence.FindingRepository;
import io.github.artemiscore.artemisappsec.project.service.ProjectFindingStats;

@Component
@Transactional(readOnly = true)
class FindingStatsProvider implements ProjectFindingStats {

	private final FindingRepository findings;

	FindingStatsProvider(FindingRepository findings) {
		this.findings = findings;
	}

	@Override
	public Map<UUID, Long> openFindingsByProject(Collection<UUID> projectIds) {
		if (projectIds.isEmpty()) {
			return Map.of();
		}
		return findings.countByProjectAndStatusIn(projectIds, List.of(FindingStatus.OPEN, FindingStatus.TRIAGED)).stream()
			.collect(Collectors.toMap(FindingRepository.ProjectCount::getProjectId, FindingRepository.ProjectCount::getTotal));
	}
}
