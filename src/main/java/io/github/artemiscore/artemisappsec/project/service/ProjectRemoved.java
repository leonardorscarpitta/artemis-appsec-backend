package io.github.artemiscore.artemisappsec.project.service;

import java.util.UUID;

/** A repository was removed with its history kept: nothing may be scanned for it any more. */
public record ProjectRemoved(UUID projectId, UUID organizationId) {
}
