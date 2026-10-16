package io.github.artemiscore.artemisappsec.project.service;

import java.util.Map;
import java.util.UUID;

/**
 * Open finding counts per project, provided by the finding module. Declared here so the project module does not
 * depend on the finding module.
 */
public interface ProjectFindingStats {

	Map<UUID, Long> openFindingsByProject();
}
