package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Map;
import java.util.UUID;

/** Port implemented by the project module: repositories (not removed) per organization. */
public interface RepositoryCounts {

	Map<UUID, Long> repositoriesByOrganization();
}
