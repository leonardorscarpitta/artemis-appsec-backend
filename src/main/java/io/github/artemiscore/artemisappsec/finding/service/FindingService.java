package io.github.artemiscore.artemisappsec.finding.service;

import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** Public contract of the finding service (vulnerabilities, secrets, code, configuration and license issues). */
public interface FindingService {

	List<FindingSummary> search(UUID organizationId, FindingQuery query);

	FindingDetails get(UUID organizationId, UUID id);

	/** Accepting requires a justification and an expiry date and records a risk acceptance. */
	FindingDetails changeStatus(UUID organizationId, UUID id, ChangeStatusCommand command, String actor);

	/** Findings of the organization that still need attention (open or triaged). */
	List<FindingSummary> findActive(UUID organizationId);

	/**
	 * Records everything a completed scanner stage reported for a project: new issues are opened, known ones refreshed
	 * (and reopened if they had been fixed), and active issues of that stage that were not reported again are fixed.
	 */
	IngestionResult recordDetections(UUID projectId, Stage stage, String tool, List<DetectedIssue> issues);
}
