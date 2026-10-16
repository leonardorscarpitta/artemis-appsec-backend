package io.github.artemiscore.artemis.pipelinerun.service;

import io.github.artemiscore.artemis.pipelinerun.domain.RunStatus;

public record RunFilter(String repository, RunStatus status, String branch) {
}
