package io.github.artemiscore.artemisappsec.billing.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;
import io.github.artemiscore.artemisappsec.billing.domain.Plan;
import io.github.artemiscore.artemisappsec.billing.domain.Subscription;
import io.github.artemiscore.artemisappsec.billing.persistence.SubscriptionRepository;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

@Service
@Transactional(readOnly = true)
class BillingServiceImpl implements BillingService {

	private final SubscriptionRepository subscriptions;
	private final boolean enabled;

	BillingServiceImpl(SubscriptionRepository subscriptions, ArtemisAppSecProperties properties) {
		this.subscriptions = subscriptions;
		this.enabled = properties.billing() != null && properties.billing().enabled();
	}

	@Override
	public BillingOverview overview() {
		return toOverview(current());
	}

	@Override
	@Transactional
	public BillingOverview changePlan(ChangePlanCommand command) {
		if (!enabled) {
			throw new BusinessRuleException("Billing is not available yet");
		}
		Subscription subscription = current();
		if (subscription.getPlan() == command.plan()) {
			throw new BusinessRuleException("The organization is already on the %s plan".formatted(command.plan().name()));
		}
		// No payment platform is integrated yet, so paid plans cannot be purchased.
		if (command.plan().isPaid()) {
			throw new BusinessRuleException("Payments are not available yet");
		}
		subscription.changeTo(command.plan());
		return toOverview(subscription);
	}

	@Override
	public boolean isEntitled(Feature feature) {
		return current().getPlan().features().contains(feature);
	}

	private Subscription current() {
		return subscriptions.findById(Subscription.SINGLETON_ID).orElseThrow();
	}

	private BillingOverview toOverview(Subscription subscription) {
		return new BillingOverview(enabled, subscription.getPlan(), subscription.getStartedAt(),
				Arrays.stream(Plan.values()).map(PlanView::of).toList(),
				subscription.getPlan().features().stream().sorted().toList());
	}
}
