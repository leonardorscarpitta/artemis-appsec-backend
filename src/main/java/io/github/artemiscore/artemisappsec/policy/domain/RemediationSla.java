package io.github.artemiscore.artemisappsec.policy.domain;

import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maximum number of days to fix a finding of a given severity. */
@Entity
@Table(name = "policy_remediation_sla")
public class RemediationSla {

	public static final int MIN_DAYS = 1;
	public static final int MAX_DAYS = 365;

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Severity severity;

	@Column(nullable = false)
	private int days;

	protected RemediationSla() {
	}

	public RemediationSla(UUID organizationId, Severity severity, int days) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.severity = severity;
		setDays(days);
	}

	public void setDays(int days) {
		if (days < MIN_DAYS || days > MAX_DAYS) {
			throw new IllegalArgumentException("SLA days must be between %d and %d".formatted(MIN_DAYS, MAX_DAYS));
		}
		this.days = days;
	}

	public Severity getSeverity() {
		return severity;
	}

	public int getDays() {
		return days;
	}
}
