package io.github.artemiscore.artemisappsec.billing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An organization's current plan; every organization starts on FREE. */
@Entity
@Table(name = "subscription")
public class Subscription {

	@Id
	@Column(name = "organization_id")
	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Plan plan;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	protected Subscription() {
	}

	public static Subscription free(UUID organizationId) {
		Subscription subscription = new Subscription();
		subscription.organizationId = organizationId;
		subscription.changeTo(Plan.FREE);
		return subscription;
	}

	public void changeTo(Plan plan) {
		this.plan = plan;
		this.startedAt = Instant.now();
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public Plan getPlan() {
		return plan;
	}

	public Instant getStartedAt() {
		return startedAt;
	}
}
