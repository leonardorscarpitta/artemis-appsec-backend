package io.github.artemiscore.artemisappsec.policy.domain;

import java.util.UUID;

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
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "license_id", nullable = false)
	private String licenseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LicenseDecision decision;

	protected LicenseRule() {
	}

	public LicenseRule(UUID organizationId, String licenseId, LicenseDecision decision) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
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
