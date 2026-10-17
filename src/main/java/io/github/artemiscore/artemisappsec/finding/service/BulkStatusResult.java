package io.github.artemiscore.artemisappsec.finding.service;

/** {@code skipped}: findings already in that status or that cannot move to it (e.g. a fixed finding to triaged). */
public record BulkStatusResult(int updated, int skipped) {
}
