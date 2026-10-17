package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunTrigger;

/** {@code repositoryRemoved}: the run belongs to a removed repository whose history was kept. */
public record RunView(long number, String repository, boolean repositoryRemoved, String branch, String commit, String message, RunTrigger trigger,
		String actor, RunStatus status, Instant startedAt, int durationSeconds, int newFindings, int totalFindings,
		boolean failedByPolicy, List<String> folders) {
}
