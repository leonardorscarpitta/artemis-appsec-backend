package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Port implemented by the billing module: each organization's plan and what it allows. */
public interface PlanLookup {

	Map<UUID, String> plansOf(Collection<UUID> organizationIds);

	boolean canRename(UUID organizationId);
}
