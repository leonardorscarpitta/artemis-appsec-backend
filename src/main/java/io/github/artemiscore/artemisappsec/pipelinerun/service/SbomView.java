package io.github.artemiscore.artemisappsec.pipelinerun.service;

/** An SBOM stored with a run; {@code folder} is the monorepo folder, or empty for the whole repository. */
public record SbomView(String folder, int components, int sizeBytes) {
}
