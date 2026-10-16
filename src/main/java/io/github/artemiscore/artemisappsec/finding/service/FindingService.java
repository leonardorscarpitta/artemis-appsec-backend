package io.github.artemiscore.artemisappsec.finding.service;

import java.util.List;
import java.util.UUID;

/** Public contract of the finding (vulnerability) service. */
public interface FindingService {

	List<FindingSummary> search(FindingQuery query);

	FindingDetails get(UUID id);

	FindingDetails changeStatus(UUID id, ChangeStatusCommand command);

	/** Findings that still need attention (open or triaged). */
	List<FindingSummary> findActive();
}
