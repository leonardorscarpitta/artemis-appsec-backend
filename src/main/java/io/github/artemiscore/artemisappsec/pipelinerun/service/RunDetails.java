package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.util.List;

public record RunDetails(RunView run, List<StageRunView> stages) {
}
