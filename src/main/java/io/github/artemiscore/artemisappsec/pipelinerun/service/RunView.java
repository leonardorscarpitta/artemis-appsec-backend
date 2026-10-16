package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Instant;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunTrigger;

public record RunView(long number, String repository, String branch, String commit, String message, RunTrigger trigger,
		String actor, RunStatus status, Instant startedAt, int durationSeconds, int newFindings, int totalFindings,
		boolean failedByPolicy) {
}
