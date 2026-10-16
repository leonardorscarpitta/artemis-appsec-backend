package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** What the scanner runner must do for one claimed run. */
public record JobAssignment(UUID runId, long number, Repository repository, List<StageJob> stages, Limits limits) {

	/** @param paths monorepo folders to scan inside the clone; empty for the whole repository */
	public record Repository(String name, String cloneUrl, String branch, List<String> paths) {
	}

	public record StageJob(Stage id, String tool, FailThreshold failOn) {
	}

	public record Limits(int stageTimeoutSeconds, int maxRepoSizeMb) {
	}
}
