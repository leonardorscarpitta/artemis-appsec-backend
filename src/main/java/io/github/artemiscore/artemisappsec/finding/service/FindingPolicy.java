package io.github.artemiscore.artemisappsec.finding.service;

import java.util.Map;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/** Organization rules applied to findings (remediation SLAs, license policy), implemented by the policy module. */
public interface FindingPolicy {

	/** Days to fix a finding, per severity; severities without an SLA are absent. */
	Map<Severity, Integer> remediationDays(UUID organizationId);

	/** The organization's decision for an SPDX license identifier (case-insensitive). */
	LicenseVerdict license(UUID organizationId, String licenseId);

	enum LicenseVerdict {
		/** Allowed: not reported. */
		ALLOW,
		/** Denied: reported as high severity. */
		DENY,
		/** No rule: reported as medium severity for review. */
		REVIEW
	}
}
