package io.github.artemiscore.artemisappsec.dashboard.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.dashboard.service.DashboardService;
import io.github.artemiscore.artemisappsec.dashboard.service.DashboardSummary;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Security overview across the organization's repositories")
class DashboardController {

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/summary")
	DashboardSummary summary(Tenant tenant) {
		return dashboard.summary(tenant.organizationId());
	}
}
