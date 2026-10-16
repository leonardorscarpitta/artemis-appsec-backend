package io.github.artemiscore.artemis.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy_license_rule")
public class LicenseRule {

	@Id
	@Column(name = "license_id")
	private String licenseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LicenseDecision decision;

	protected LicenseRule() {
	}

	public LicenseRule(String licenseId, LicenseDecision decision) {
		this.licenseId = licenseId;
		this.decision = decision;
	}

	public String getLicenseId() {
		return licenseId;
	}

	public LicenseDecision getDecision() {
		return decision;
	}
}
