package io.github.artemiscore.artemisappsec.finding.persistence;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

public final class FindingSpecifications {

	private FindingSpecifications() {
	}

	public static Specification<Finding> matchesText(String text) {
		String pattern = "%" + text.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("cve")), pattern, '\\'),
				cb.like(cb.lower(root.get("packageName")), pattern, '\\'),
				cb.like(cb.lower(root.get("title")), pattern, '\\'));
	}

	public static Specification<Finding> hasStatus(FindingStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<Finding> inProject(UUID projectId) {
		return (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
	}

	public static Specification<Finding> hasSeverity(Severity severity) {
		BigDecimal min = switch (severity) {
			case CRITICAL -> new BigDecimal("9.0");
			case HIGH -> new BigDecimal("7.0");
			case MEDIUM -> new BigDecimal("4.0");
			case LOW -> BigDecimal.ZERO;
		};
		BigDecimal max = switch (severity) {
			case CRITICAL -> new BigDecimal("10.0");
			case HIGH -> new BigDecimal("8.9");
			case MEDIUM -> new BigDecimal("6.9");
			case LOW -> new BigDecimal("3.9");
		};
		return (root, query, cb) -> cb.between(root.get("cvssScore"), min, max);
	}
}
