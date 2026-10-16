package io.github.artemiscore.artemis.pipelinerun.service;

import java.util.List;

public record RunDetails(RunView run, List<StageRunView> stages) {
}
