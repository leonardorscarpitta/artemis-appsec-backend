package io.github.artemiscore.artemisappsec.project.service;

import java.util.UUID;

/** Published after a repository is connected; other modules react to it (e.g. queueing its first scan). */
/** {@code privateRepository}: SSH repositories wait for their deploy key to be added, so no first scan is queued. */
public record ProjectCreated(UUID projectId, UUID organizationId, String name, String actor, boolean privateRepository) {
}
