package io.github.artemiscore.artemisappsec.project.service;

import java.util.UUID;

/** Published after a repository is connected; other modules react to it (e.g. queueing its first scan). */
public record ProjectCreated(UUID projectId, UUID organizationId, String name, String actor) {
}
