package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Port implemented by the billing module: the plan code of each organization. */
public interface PlanLookup {

	Map<UUID, String> plansOf(Collection<UUID> organizationIds);
}
