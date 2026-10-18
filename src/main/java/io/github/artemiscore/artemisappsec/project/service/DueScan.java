package io.github.artemiscore.artemisappsec.project.service;

import java.util.UUID;

/** A repository whose scheduled scan is due. */
public record DueScan(UUID projectId, UUID organizationId, String name) {
}
