package io.github.artemiscore.artemisappsec.dashboard.service;

import java.time.ZoneId;
import java.util.UUID;

public interface DashboardService {

	DashboardSummary summary(UUID organizationId);

	/** History of the last {@code days} days (today included) in {@code zone}. */
	DashboardTrends trends(UUID organizationId, int days, ZoneId zone);
}
