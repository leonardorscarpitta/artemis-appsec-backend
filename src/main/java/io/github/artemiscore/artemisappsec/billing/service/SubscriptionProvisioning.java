package io.github.artemiscore.artemisappsec.billing.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.billing.domain.Subscription;
import io.github.artemiscore.artemisappsec.billing.persistence.SubscriptionRepository;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationCreated;
import io.github.artemiscore.artemisappsec.organization.service.PlanLookup;

/** New organizations start on FREE; also tells the organization module each organization's plan. */
@Component
class SubscriptionProvisioning implements PlanLookup {

	private final SubscriptionRepository subscriptions;

	SubscriptionProvisioning(SubscriptionRepository subscriptions) {
		this.subscriptions = subscriptions;
	}

	@EventListener
	void onOrganizationCreated(OrganizationCreated event) {
		subscriptions.save(Subscription.free(event.organizationId()));
	}

	@Override
	public Map<UUID, String> plansOf(Collection<UUID> organizationIds) {
		return subscriptions.findAllById(organizationIds).stream()
			.collect(Collectors.toMap(Subscription::getOrganizationId, s -> s.getPlan().wireValue()));
	}
}
