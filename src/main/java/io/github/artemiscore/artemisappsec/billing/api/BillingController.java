package io.github.artemiscore.artemisappsec.billing.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.billing.service.BillingOverview;
import io.github.artemiscore.artemisappsec.billing.service.BillingService;
import io.github.artemiscore.artemisappsec.billing.service.ChangePlanCommand;
import io.github.artemiscore.artemisappsec.billing.service.PlanView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Plans and the organization's subscription")
class BillingController {

	private final BillingService billing;

	BillingController(BillingService billing) {
		this.billing = billing;
	}

	@GetMapping
	BillingOverview overview() {
		return billing.overview();
	}

	/** Public: shown on the home page. */
	@GetMapping("/plans")
	List<PlanView> plans() {
		return billing.plans();
	}

	@PutMapping("/subscription")
	BillingOverview changePlan(@Valid @RequestBody ChangePlanCommand command) {
		return billing.changePlan(command);
	}
}
