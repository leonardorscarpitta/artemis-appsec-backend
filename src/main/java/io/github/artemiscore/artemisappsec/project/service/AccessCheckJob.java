package io.github.artemiscore.artemisappsec.project.service;

import java.util.UUID;

/** A claimed access check, for the scanner job API. */
public record AccessCheckJob(UUID checkId, UUID projectId, String name, String cloneUrl, String branch) {
}
