package io.github.artemiscore.artemisappsec.billing.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The organization's current plan (one organization per deployment, so a single row). */
@Entity
@Table(name = "subscription")
public class Subscription {

	public static final int SINGLETON_ID = 1;

	@Id
	private Integer id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Plan plan;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	protected Subscription() {
	}

	public void changeTo(Plan plan) {
		this.plan = plan;
		this.startedAt = Instant.now();
	}

	public Plan getPlan() {
		return plan;
	}

	public Instant getStartedAt() {
		return startedAt;
	}
}
