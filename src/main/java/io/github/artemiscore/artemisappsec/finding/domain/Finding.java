package io.github.artemiscore.artemisappsec.finding.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** A known vulnerability (CVE) detected in a package of a monitored repository. */
@Entity
@Table(name = "finding")
public class Finding {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String reference;

	@Column(nullable = false)
	private String cve;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(name = "package_name", nullable = false)
	private String packageName;

	@Column(name = "package_group")
	private String packageGroup;

	@Column(name = "installed_version", nullable = false)
	private String installedVersion;

	@Column(name = "fixed_version")
	private String fixedVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Ecosystem ecosystem;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(name = "cvss_vector", nullable = false)
	private String cvssVector;

	@Column(name = "cvss_score", nullable = false, precision = 3, scale = 1)
	private BigDecimal cvssScore;

	private String cwe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Column(nullable = false)
	private String tool;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingStatus status;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FindingEvent> events = new ArrayList<>();

	protected Finding() {
	}

	public void changeStatus(FindingStatus target, String note, Instant now) {
		if (target == status) {
			throw new BusinessRuleException("Finding %s is already %s".formatted(reference, target.code()));
		}
		if (!status.canTransitionTo(target)) {
			throw new BusinessRuleException(
					"Finding %s cannot change from %s to %s".formatted(reference, status.code(), target.code()));
		}
		this.status = target;
		this.statusChangedAt = now;
		events.add(new FindingEvent(this, FindingEventType.STATUS_CHANGED, target, note, now));
	}

	public List<FindingEvent> getTimeline() {
		return events.stream().sorted(Comparator.comparing(FindingEvent::getOccurredAt)).toList();
	}

	public UUID getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public String getCve() {
		return cve;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getPackageGroup() {
		return packageGroup;
	}

	public String getInstalledVersion() {
		return installedVersion;
	}

	public String getFixedVersion() {
		return fixedVersion;
	}

	public Ecosystem getEcosystem() {
		return ecosystem;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCvssVector() {
		return cvssVector;
	}

	public BigDecimal getCvssScore() {
		return cvssScore;
	}

	public String getCwe() {
		return cwe;
	}

	public Stage getStage() {
		return stage;
	}

	public String getTool() {
		return tool;
	}

	public FindingStatus getStatus() {
		return status;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}
}
