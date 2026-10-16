package io.github.artemiscore.artemisappsec.billing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.billing.domain.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {
}
