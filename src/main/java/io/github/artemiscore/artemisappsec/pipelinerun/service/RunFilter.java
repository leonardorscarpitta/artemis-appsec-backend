package io.github.artemiscore.artemisappsec.pipelinerun.service;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;

public record RunFilter(String repository, RunStatus status, String branch) {
}
