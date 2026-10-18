package io.github.artemiscore.artemisappsec.usage.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.usage.service.UsageOverview;
import io.github.artemiscore.artemisappsec.usage.service.UsageService;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage", description = "The organization's monthly scan quota and enabled features")
class UsageController {

	private final UsageService usage;

	UsageController(UsageService usage) {
		this.usage = usage;
	}

	@GetMapping
	UsageOverview overview(Tenant tenant) {
		return usage.overview(tenant.organizationId());
	}
}
