package io.github.artemiscore.artemis.finding.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemis.finding.domain.FindingStatus;
import io.github.artemiscore.artemis.finding.persistence.FindingRepository;
import io.github.artemiscore.artemis.project.service.ProjectFindingStats;

@Component
@Transactional(readOnly = true)
class FindingStatsProvider implements ProjectFindingStats {

	private final FindingRepository findings;

	FindingStatsProvider(FindingRepository findings) {
		this.findings = findings;
	}

	@Override
	public Map<UUID, Long> openFindingsByProject() {
		return findings.countByProjectAndStatusIn(List.of(FindingStatus.OPEN, FindingStatus.TRIAGED)).stream()
			.collect(Collectors.toMap(FindingRepository.ProjectCount::getProjectId, FindingRepository.ProjectCount::getTotal));
	}
}
