package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Instant;

/**
 * An SBOM of the most recent run with SBOMs of a repository: {@code folder} is the monorepo folder (empty = whole
 * repository) and {@code gzipped} the gzip-compressed CycloneDX JSON.
 */
public record LatestSbom(String repository, long runNumber, Instant scannedAt, String folder, byte[] gzipped) {
}
