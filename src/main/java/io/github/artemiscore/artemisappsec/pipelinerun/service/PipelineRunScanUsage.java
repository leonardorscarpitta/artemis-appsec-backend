package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.billing.service.ScanUsage;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunRepository;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;

/** Every pipeline run of the organization's repositories counts as one scan. */
@Component
class PipelineRunScanUsage implements ScanUsage {

	private final PipelineRunRepository runs;
	private final ProjectService projects;

	PipelineRunScanUsage(PipelineRunRepository runs, ProjectService projects) {
		this.runs = runs;
		this.projects = projects;
	}

	@Override
	@Transactional(readOnly = true)
	public long scansSince(UUID organizationId, Instant since) {
		List<UUID> projectIds = projects.findAll(organizationId).stream().map(ProjectSummary::id).toList();
		return projectIds.isEmpty() ? 0 : runs.countByProjectIdInAndStartedAtGreaterThanEqual(projectIds, since);
	}
}
