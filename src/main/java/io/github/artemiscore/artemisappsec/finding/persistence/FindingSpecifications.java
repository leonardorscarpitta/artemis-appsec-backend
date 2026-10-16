package io.github.artemiscore.artemisappsec.finding.persistence;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
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
				cb.like(cb.lower(root.get("ruleId")), pattern, '\\'),
				cb.like(cb.lower(root.get("locationPath")), pattern, '\\'),
				cb.like(cb.lower(root.get("title")), pattern, '\\'));
	}

	public static Specification<Finding> hasStatus(FindingStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<Finding> inProject(UUID projectId) {
		return (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
	}

	public static Specification<Finding> hasSeverity(Severity severity) {
		return (root, query, cb) -> cb.equal(root.get("severity"), severity);
	}

	public static Specification<Finding> hasKind(FindingKind kind) {
		return (root, query, cb) -> cb.equal(root.get("kind"), kind);
	}
}
