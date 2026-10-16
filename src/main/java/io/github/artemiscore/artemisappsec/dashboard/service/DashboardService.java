package io.github.artemiscore.artemisappsec.dashboard.service;

import java.util.UUID;

public interface DashboardService {

	DashboardSummary summary(UUID organizationId);
}
