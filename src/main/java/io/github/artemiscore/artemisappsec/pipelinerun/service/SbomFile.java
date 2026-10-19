package io.github.artemiscore.artemisappsec.pipelinerun.service;

/** A stored SBOM for download: {@code gzipped} is the gzip-compressed CycloneDX JSON of {@code sizeBytes} bytes. */
public record SbomFile(String repository, long runNumber, String folder, byte[] gzipped, int sizeBytes) {
}
