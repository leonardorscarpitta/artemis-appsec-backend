package io.github.artemiscore.artemisappsec.billing.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.billing.domain.Feature;
import io.github.artemiscore.artemisappsec.billing.domain.Plan;
import io.github.artemiscore.artemisappsec.billing.domain.Subscription;
import io.github.artemiscore.artemisappsec.billing.persistence.ScanUsageCounter;
import io.github.artemiscore.artemisappsec.billing.persistence.SubscriptionRepository;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

@Service
@Transactional(readOnly = true)
class BillingServiceImpl implements BillingService {

	private final SubscriptionRepository subscriptions;
	private final ScanUsageCounter usage;
	private final boolean enabled;
	private final Clock clock = Clock.systemUTC();

	BillingServiceImpl(SubscriptionRepository subscriptions, ScanUsageCounter usage, ArtemisAppSecProperties properties) {
		this.subscriptions = subscriptions;
		this.usage = usage;
		this.enabled = properties.billing() != null && properties.billing().enabled();
	}

	@Override
	public BillingOverview overview(UUID organizationId) {
		return toOverview(current(organizationId));
	}

	@Override
	@Transactional
	public BillingOverview changePlan(UUID organizationId, ChangePlanCommand command) {
		if (!enabled) {
			throw new BusinessRuleException("Billing is not available yet");
		}
		Subscription subscription = current(organizationId);
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
	@Transactional
	public LocalDate consumeScan(UUID organizationId) {
		int limit = current(organizationId).getPlan().monthlyScanLimit();
		LocalDate month = currentMonth();
		if (!usage.tryIncrement(organizationId, month, limit)) {
			throw new BusinessRuleException("Monthly scan limit reached (%d scans on the current plan)".formatted(limit));
		}
		return month;
	}

	@Override
	@Transactional
	public void refundScan(UUID organizationId, LocalDate month) {
		usage.decrement(organizationId, month);
	}

	@Override
	public boolean isEntitled(UUID organizationId, Feature feature) {
		return current(organizationId).getPlan().features().contains(feature);
	}

	private Subscription current(UUID organizationId) {
		return subscriptions.findById(organizationId)
			.orElseThrow(() -> new IllegalStateException("Organization %s has no subscription".formatted(organizationId)));
	}

	private LocalDate currentMonth() {
		return LocalDate.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1);
	}

	private BillingOverview toOverview(Subscription subscription) {
		LocalDate month = currentMonth();
		UsageView usageView = new UsageView(usage.scans(subscription.getOrganizationId(), month), subscription.getPlan().monthlyScanLimit(),
				month.atStartOfDay(ZoneOffset.UTC).toInstant());
		return new BillingOverview(enabled, subscription.getPlan(), subscription.getStartedAt(), plans(),
				subscription.getPlan().features().stream().sorted().toList(), usageView);
	}
}
