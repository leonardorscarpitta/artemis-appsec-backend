package io.github.artemiscore.artemisappsec.billing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

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
	private final ScanUsage usage;
	private final boolean enabled;
	private final Clock clock = Clock.systemUTC();

	BillingServiceImpl(SubscriptionRepository subscriptions, ScanUsage usage, ArtemisAppSecProperties properties) {
		this.subscriptions = subscriptions;
		this.usage = usage;
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
	public List<PlanView> plans() {
		return Arrays.stream(Plan.values()).map(PlanView::of).toList();
	}

	@Override
	public void assertCanScan() {
		int limit = current().getPlan().monthlyScanLimit();
		if (usage.scansSince(periodStart()) >= limit) {
			throw new BusinessRuleException("Monthly scan limit reached (%d scans on the current plan)".formatted(limit));
		}
	}

	@Override
	public boolean isEntitled(Feature feature) {
		return current().getPlan().features().contains(feature);
	}

	private Subscription current() {
		return subscriptions.findById(Subscription.SINGLETON_ID).orElseThrow();
	}

	private Instant periodStart() {
		return ZonedDateTime.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	private BillingOverview toOverview(Subscription subscription) {
		Instant start = periodStart();
		UsageView usageView = new UsageView(usage.scansSince(start), subscription.getPlan().monthlyScanLimit(), start);
		return new BillingOverview(enabled, subscription.getPlan(), subscription.getStartedAt(), plans(),
				subscription.getPlan().features().stream().sorted().toList(), usageView);
	}
}
