package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.util.List;

/** {@code sboms}: the SBOMs stored by the run's SBOM stage (empty for older runs or when none was generated). */
public record RunDetails(RunView run, List<StageRunView> stages, List<SbomView> sboms) {
}
